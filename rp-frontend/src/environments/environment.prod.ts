export const environment = {
    production: true,
    // Same-origin deployment: frontend and backend live behind the same domain,
    // with the web server proxying /api/** to the Spring Boot app (see DEPLOY.md).
    // This keeps the build host-agnostic — no domain ever needs to be baked in.
    // If the backend is ever hosted on a DIFFERENT domain, put its full URL here
    // (e.g. 'https://api.example.com') and rebuild.
    apiUrl: 'https://avpro-backend.rpmediagroup.co'
};
