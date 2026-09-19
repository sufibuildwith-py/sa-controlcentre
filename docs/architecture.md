# Phase 1 architecture

The Tauri desktop hosts a React client. The client calls a Spring Boot API over configured HTTP and sends credentials with every request. Authentication uses a server-side JDBC session with an HttpOnly, SameSite cookie; no secret is stored in localStorage. PostgreSQL owns users, sessions, employees, attendance, leave and audit records. Typed frontend mock data is isolated under the Command feature for future Phase 2/3 cards only.

