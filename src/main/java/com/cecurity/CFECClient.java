package com.cecurity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * REST client for the CFEC Vault/Safe/Folder API.
 *
 * Executes three chained API calls:
 *   1. authenticate()   — POST /auth          → obtains the JSESSIONID cookie
 *   2. getFolderNode()  — POST /folders/{id}  → retrieves a folder node
 *   3. createFolder()   — POST /folders       — creates a new folder
 *
 * The JSESSIONID from call 1 is forwarded automatically to calls 2 and 3.
 *
 * Java 11+ HttpClient — no external dependencies required.
 */
public class CFECClient {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------
    private static final String BASE_URL =
            "https://partition-rcte.cecurity.com/jersey-cfec-openapi/rest";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Single shared HttpClient — thread-safe, can be reused across all calls.
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------
    public static void main(String[] args) throws Exception {

        // --- Shared parameters ---
        String myCFEC    = "525";   // replace with your value
        String mySAFE    = "5752";   // replace with your value

        // --- Credentials for authentication ---
        String username  = "bruno";          // replace with your username
        String password  = "iZjfETr0HeWvF!Vb";         // replace with your password

        // ===== CALL 1 — Authenticate ========================================
        System.out.println("========== CALL 1 — AUTHENTICATE ==========");
        ApiResponse authResponse = authenticate(myCFEC, mySAFE, username, password);
        printResponse(authResponse);

        if (authResponse.statusCode() != 200) {
            System.err.println("[ERROR] Authentication failed — aborting.");
            return;
        }

        String sessionCookie = extractSessionCookie(authResponse);
        if (sessionCookie == null) {
            System.err.println("[ERROR] No JSESSIONID in auth response — aborting.");
            return;
        }
        System.out.println("\n[INFO] Session established: " + sessionCookie);

        // ===== CALL 2 — Create folder =======================================
        String homeFolder    = "52218";                               // replace with your homeFolder id
        String newFolderName = "import/test1/root"; // replace as needed

        System.out.println("\n========== CALL 2 — CREATE FOLDER ==========");
        ApiResponse createResponse = createFolder(
                myCFEC, mySAFE, homeFolder, "HOME", newFolderName, sessionCookie);
        printResponse(createResponse);

        // ===== CALL 3 — Get folder node =====================================
        String myFolderId = "import/test1/root"; // replace with your value

        System.out.println("\n========== CALL 3 — GET FOLDER NODE ==========");
        ApiResponse folderResponse = getFolderNode(myCFEC, mySAFE, myFolderId, sessionCookie);
        printResponse(folderResponse);

    }

    // -----------------------------------------------------------------------
    // Call 1 — Authenticate
    // -----------------------------------------------------------------------
    /**
     * POSTs credentials to {@code /vaults/{cfec}/safes/{safe}/auth}.
     * On success the server returns a {@code Set-Cookie: JSESSIONID=…} header
     * that must be forwarded as the {@code Cookie} header in all subsequent calls.
     *
     * @param cfec     CFEC partition identifier
     * @param safe     safe identifier
     * @param username login name
     * @param password plain-text password
     * @return {@link ApiResponse} containing status, headers, cookies and body
     */
    public static ApiResponse authenticate(
            String cfec,
            String safe,
            String username,
            String password) throws Exception {

        String url = String.format("%s/vaults/%s/safes/%s/auth", BASE_URL, cfec, safe);

        String body = String.format(
                "{%n" +
                        "    \"auth\": {%n" +
                        "        \"loginName\": \"%s\",%n" +
                        "        \"password\": \"%s\"%n" +
                        "    }%n" +
                        "}",
                username, password);

        return post(url, body, null);
    }

    // -----------------------------------------------------------------------
    // Call 2 — Get folder node
    // -----------------------------------------------------------------------
    /**
     * POSTs to {@code /vaults/{cfec}/safes/{safe}/folders/{folderId}} to retrieve
     * the metadata of an existing folder/archive node.
     *
     * @param cfec          CFEC partition identifier
     * @param safe          safe identifier
     * @param folderId      id of the folder or archive node to retrieve
     * @param sessionCookie cookie string in the form {@code "JSESSIONID=…"}
     *                      obtained from {@link #authenticate}
     * @return {@link ApiResponse} containing status, headers, cookies and body
     */
    public static ApiResponse getFolderNode(
            String cfec,
            String safe,
            String folderId,
            String sessionCookie) throws Exception {

        String url = String.format("%s/vaults/%s/safes/%s/folders/%s",
                BASE_URL, cfec, safe, folderId);

        return post(url, "{}", sessionCookie);
    }

    // -----------------------------------------------------------------------
    // Call 3 — Create folder
    // -----------------------------------------------------------------------
    /**
     * POSTs to {@code /vaults/{cfec}/safes/{safe}/folders} to create a new folder.
     *
     * @param cfec          CFEC partition identifier
     * @param safe          safe identifier
     * @param parentId      id of the parent folder (e.g. the home folder id)
     * @param parentPath    logical path of the parent folder (e.g. {@code "HOME"})
     * @param folderName    name or relative path of the folder to create
     * @param sessionCookie cookie string in the form {@code "JSESSIONID=…"}
     *                      obtained from {@link #authenticate}
     * @return {@link ApiResponse} containing status, headers, cookies and body
     */
    public static ApiResponse createFolder(
            String cfec,
            String safe,
            String parentId,
            String parentPath,
            String folderName,
            String sessionCookie) throws Exception {

        String url = String.format("%s/vaults/%s/safes/%s/folders", BASE_URL, cfec, safe);

        String body = String.format(
                "{%n" +
                        "    \"parentId\": \"%s\",%n" +
                        "    \"parentPath\": \"%s\",%n" +
                        "    \"name\": \"%s\"%n" +
                        "}",
                parentId, parentPath, folderName);

        return post(url, body, sessionCookie);
    }

    // -----------------------------------------------------------------------
    // Shared HTTP POST — single place for all boilerplate
    // -----------------------------------------------------------------------
    /**
     * Executes an HTTP POST request and returns a fully populated
     * {@link ApiResponse}.
     *
     * <p>This private method is the single entry point for all network I/O in
     * this class; the three public API methods delegate to it. Keeping the
     * HTTP mechanics here avoids duplication and makes future changes (e.g.
     * adding request logging or retry logic) trivial.
     *
     * @param url           absolute URL to POST to
     * @param jsonBody      request body (must be valid JSON)
     * @param sessionCookie optional {@code "JSESSIONID=…"} value forwarded as
     *                      the {@code Cookie} header; {@code null} to omit
     * @return {@link ApiResponse} with status code, headers, cookies and body
     */
    private static ApiResponse post(
            String url,
            String jsonBody,
            String sessionCookie) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept",       "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonBody != null ? jsonBody : ""));

        if (sessionCookie != null && !sessionCookie.isBlank()) {
            builder.header("Cookie", sessionCookie);
        }

        HttpResponse<String> raw =
                HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        // Collect all response headers
        Map<String, List<String>> headers = new LinkedHashMap<>();
        raw.headers().map().forEach((k, v) ->
                headers.put(k == null ? "(status)" : k, v));

        // Parse every Set-Cookie header
        List<ParsedCookie> cookies = new ArrayList<>();
        for (String setCookie : raw.headers().allValues("set-cookie")) {
            cookies.add(ParsedCookie.parse(setCookie));
        }

        return new ApiResponse(raw.statusCode(), headers, cookies, raw.body());
    }

    // -----------------------------------------------------------------------
    // Helper — extract JSESSIONID ready for use as a Cookie header value
    // -----------------------------------------------------------------------
    /**
     * Scans the cookies of an {@link ApiResponse} for a {@code JSESSIONID}
     * entry and returns it formatted as {@code "JSESSIONID=<value>"}, which
     * can be passed directly as the {@code Cookie} request header.
     *
     * @return the formatted cookie string, or {@code null} if not present
     */
    public static String extractSessionCookie(ApiResponse response) {
        return response.cookies().stream()
                .filter(c -> "JSESSIONID".equalsIgnoreCase(c.name()))
                .findFirst()
                .map(c -> "JSESSIONID=" + c.value())
                .orElse(null);
    }

    // -----------------------------------------------------------------------
    // Helper — print an ApiResponse to stdout
    // -----------------------------------------------------------------------
    private static void printResponse(ApiResponse response) {
        System.out.println("\n--- HTTP Status ---");
        System.out.println(response.statusCode());

        System.out.println("\n--- Response Headers ---");
        response.headers().forEach((name, values) ->
                values.forEach(v -> System.out.println(name + ": " + v)));

        System.out.println("\n--- Cookies (from Set-Cookie) ---");
        if (response.cookies().isEmpty()) {
            System.out.println("(none)");
        } else {
            response.cookies().forEach(c ->
                    System.out.println(c.name() + " = " + c.value()
                            + (c.maxAge()  != null ? "  (Max-Age=" + c.maxAge() + ")"  : "")
                            + (c.expires() != null ? "  (Expires=" + c.expires() + ")" : "")));
        }

        System.out.println("\n--- JSON Body ---");
        System.out.println(response.body());
    }

    // -----------------------------------------------------------------------
    // Value objects
    // -----------------------------------------------------------------------

    /** Immutable holder for everything collected from one HTTP response. */
    public record ApiResponse(
            int statusCode,
            Map<String, List<String>> headers,
            List<ParsedCookie> cookies,
            String body) {}

    /**
     * Parsed representation of one {@code Set-Cookie} header.
     * Handles the most common attributes: name/value, Max-Age, Expires, Path,
     * Secure, and HttpOnly.
     */
    public static class ParsedCookie {

        private final String  name;
        private final String  value;
        private       String  maxAge;
        private       String  expires;
        private       String  path;
        private       boolean secure;
        private       boolean httpOnly;
        private final String  raw;

        private ParsedCookie(String name, String value, String raw) {
            this.name  = name;
            this.value = value;
            this.raw   = raw;
        }

        /** Parses a raw {@code Set-Cookie} header value into a {@link ParsedCookie}. */
        public static ParsedCookie parse(String raw) {
            String[] parts     = raw.split(";");
            String[] nameValue = parts[0].trim().split("=", 2);
            String   name      = nameValue[0].trim();
            String   value     = nameValue.length > 1 ? nameValue[1].trim() : "";

            ParsedCookie cookie = new ParsedCookie(name, value, raw);

            for (int i = 1; i < parts.length; i++) {
                String attr    = parts[i].trim();
                String attrLow = attr.toLowerCase();
                if      (attrLow.startsWith("max-age="))  cookie.maxAge  = attr.substring("max-age=".length());
                else if (attrLow.startsWith("expires="))  cookie.expires = attr.substring("expires=".length());
                else if (attrLow.startsWith("path="))     cookie.path    = attr.substring("path=".length());
                else if (attrLow.equals("secure"))        cookie.secure   = true;
                else if (attrLow.equals("httponly"))      cookie.httpOnly = true;
            }
            return cookie;
        }

        public String  name()     { return name;     }
        public String  value()    { return value;    }
        public String  maxAge()   { return maxAge;   }
        public String  expires()  { return expires;  }
        public String  path()     { return path;     }
        public boolean secure()   { return secure;   }
        public boolean httpOnly() { return httpOnly; }
        public String  raw()      { return raw;      }

        @Override
        public String toString() {
            return "ParsedCookie{name='" + name + "', value='" + value +
                    "', maxAge=" + maxAge + ", expires=" + expires +
                    ", path=" + path + ", secure=" + secure +
                    ", httpOnly=" + httpOnly + "}";
        }
    }
}