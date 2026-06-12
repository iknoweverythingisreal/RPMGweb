export const environment = {
    production: true,
    // Same-origin: the app calls /api/... on whatever domain/IP serves it,
    // and nginx (rp-frontend/nginx.conf in the frontend container) proxies
    // that to the backend container. Works on any domain, no CORS, no DNS.
    // Only set a full URL here if the backend must live on a separate domain.
    apiUrl: ''
};
