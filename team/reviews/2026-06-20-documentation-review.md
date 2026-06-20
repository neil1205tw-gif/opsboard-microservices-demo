The documentation adds deployment guidance that is not executable as written for EC2: the recommended public ports remain loopback-bound, the frontend API URL instructions do not affect the Docker build, and gateway CORS is not updated for public origins. These are actionable documentation bugs that would break the documented deployment path.

Full review comments:

- [P2] Change loopback bindings before direct EC2 access — C:\side_workspace\new\docs\deployment.md:74-75
  For the direct-port EC2 option, opening 5173/8080 in the security group will not make them reachable because `docker-compose.yml` publishes both as `127.0.0.1:5173:80` and `127.0.0.1:8080:8080`. Users following this section from another machine will still get connection failures unless the docs tell them to bind those ports to the instance interface or use the reverse proxy/SSH tunnel path.

- [P2] Use the compose build arg for the public API URL — C:\side_workspace\new\docs\deployment.md:104-104
  Following the documented `docker compose up -d --build` flow, editing `frontend-dashboard/.env` does not affect the image: the Dockerfile never copies that file and `docker-compose.yml` hard-codes `VITE_API_BASE_URL: http://localhost:8080`. On EC2 this builds JS that calls the viewer's localhost instead of the instance, so document changing the compose build arg or interpolating it from the root `.env` before rebuilding.

- [P2] Include FRONTEND_ORIGIN for non-local browser origins — C:\side_workspace\new\docs\deployment.md:95-95
  When the frontend is served from an EC2 IP/domain, the browser Origin is no longer `http://localhost:5173`, but the gateway defaults CORS to `FRONTEND_ORIGIN:http://localhost:5173` and compose does not override it. If this environment section only changes MySQL/API URL settings, direct-port browser calls to `:8080` will be rejected; document setting `FRONTEND_ORIGIN` to the public frontend origin for scheme A.