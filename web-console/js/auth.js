/**
 * Google Identity Services (GIS) Authentication Module
 * Memory-only OAuth 2.0 Token Client for Google Sheets API v4
 *
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */

(function (window) {
  'use strict';

  // Strictly in-memory token storage (never persisted to localStorage, sessionStorage, or cookies)
  let _accessToken = null;
  let _tokenExpiresAt = 0;
  let _tokenClient = null;
  let _onAuthChangeCallbacks = [];

  const SCOPES = 'https://www.googleapis.com/auth/spreadsheets.readonly';

  const Auth = {
    /**
     * Initialize the GIS token client
     * @param {string} clientId Google OAuth Client ID
     * @param {Function} callback Callback when token is acquired
     */
    init(clientId, callback) {
      if (!window.google || !window.google.accounts || !window.google.accounts.oauth2) {
        console.warn('[PomGrade Auth] GIS library not yet loaded, waiting...');
        return false;
      }

      try {
        _tokenClient = window.google.accounts.oauth2.initTokenClient({
          client_id: clientId,
          scope: SCOPES,
          callback: (tokenResponse) => {
            if (tokenResponse && tokenResponse.access_token) {
              _accessToken = tokenResponse.access_token;
              const expiresIn = parseInt(tokenResponse.expires_in || '3599', 10);
              _tokenExpiresAt = Date.now() + (expiresIn * 1000);
              
              Auth._notifyAuthChange(true);
              if (typeof callback === 'function') {
                callback(_accessToken);
              }
            } else if (tokenResponse && tokenResponse.error) {
              console.error('[PomGrade Auth] GIS Error:', tokenResponse.error);
              Auth._notifyAuthChange(false, tokenResponse.error);
            }
          }
        });
        return true;
      } catch (err) {
        console.error('[PomGrade Auth] Error initializing token client:', err);
        return false;
      }
    },

    /**
     * Trigger OAuth sign-in popup
     */
    requestToken() {
      if (!_tokenClient) {
        throw new Error('Token client not initialized. Call Auth.init(clientId) first.');
      }
      _tokenClient.requestAccessToken({ prompt: _accessToken ? '' : 'consent' });
    },

    /**
     * Get active access token if still valid
     * @returns {string|null}
     */
    getAccessToken() {
      if (_accessToken && Date.now() < _tokenExpiresAt) {
        return _accessToken;
      }
      return null;
    },

    /**
     * Check if currently signed in
     * @returns {boolean}
     */
    isSignedIn() {
      return !!this.getAccessToken();
    },

    /**
     * Sign out and clear in-memory credentials
     */
    signOut() {
      if (_accessToken && window.google && window.google.accounts && window.google.accounts.oauth2) {
        try {
          window.google.accounts.oauth2.revoke(_accessToken, () => {
            console.log('[PomGrade Auth] Token revoked.');
          });
        } catch (_) {}
      }
      _accessToken = null;
      _tokenExpiresAt = 0;
      Auth._notifyAuthChange(false);
    },

    /**
     * Subscribe to authentication status changes
     * @param {Function} cb (isSignedIn, error) => void
     */
    onAuthChange(cb) {
      if (typeof cb === 'function') {
        _onAuthChangeCallbacks.push(cb);
      }
    },

    _notifyAuthChange(signedIn, error = null) {
      _onAuthChangeCallbacks.forEach(cb => {
        try { cb(signedIn, error); } catch (e) { console.error(e); }
      });
    }
  };

  window.PomGradeAuth = Auth;

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = Auth;
  }
})(typeof window !== 'undefined' ? window : global);

