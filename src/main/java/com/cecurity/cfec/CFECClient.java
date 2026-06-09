package com.cecurity.cfec;



import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.example.cfec.VaultFolderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
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
 *   2. createFolder()   — POST /folders       — creates a new folder
 *   3. listFolders()    — GET  /plan/{name}   → retrieves a folder content

 *
 * The JSESSIONID from call 1 is forwarded automatically to calls 2 and 3.
 *
 * Java 11+ HttpClient — no external dependencies required.
 */
public class CFECClient {

    private static final Logger log = LoggerFactory.getLogger(com.cecurity.cfec.CFECClient.class);

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------
    private static final String BASE_URL =
            "https://partition-rcte.cecurity.com/jersey-cfec-openapi/rest";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String PROPERTIES_RESOURCE = "/cfec.properties";

    /** Root logger name used when adjusting the log level at runtime. */
    private static final String ROOT_LOGGER = org.slf4j.Logger.ROOT_LOGGER_NAME;
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

        applyLogLevel(args);
        // ===== Load configuration ===========================================
        CFECClient.Config cfg = CFECClient.Config.load(args);
        log.debug("Configuration loaded: cfec={}, safe={}, username={}",
                cfg.cfec(), cfg.safe(), cfg.username());
        log.debug("(password is not logged)");

        // --- Shared parameters ---
        String myCFEC;//    = "525";   // replace with your value
        String mySAFE;//   = "5752";   // replace with your value

        // --- Credentials for authentication ---
        String username;//  = "bruno";          // replace with your username
        String password;//  = "iZjfETr0HeWvF!Vb";         // replace with your password


        // ===== CALL 1 — Authenticate ========================================
        log.info("========== CALL 1 — AUTHENTICATE ==========");
        ApiResponse authResponse = authenticate(cfg.cfec(), cfg.safe(), cfg.username(), cfg.password());
        logResponse(authResponse);

        if (authResponse.statusCode() != 200) {
            System.err.println("[ERROR] Authentication failed — aborting.");
            return;
        }

        String sessionCookie = extractSessionCookie(authResponse);
        if (sessionCookie == null) {
            log.info("[ERROR] No JSESSIONID in auth response — aborting.");
            return;
        }
        log.info("\n[INFO] Session established: " + sessionCookie);

        // ===== CALL 2 — Create folder =======================================
        String homeFolder    = "52218";                               // replace with your homeFolder id
        String newFolderName = "import/test1/root"; // replace as needed

        log.info("\n========== CALL 2 — CREATE FOLDER ==========");
        ApiResponse createResponse = createFolder(
                cfg.cfec(), cfg.safe(), homeFolder, "HOME", newFolderName, sessionCookie);
        logResponse(createResponse);

        // ===== CALL 3 — Get folder list =====================================
        String myFolderName = "import"; // replace with your value

        log.info("\n========== CALL 3 — GET FOLDER NODE ==========");
        ApiResponse folderResponse = listFolders(cfg.cfec(), cfg.safe(), myFolderName, sessionCookie);
        logResponse(folderResponse);

    }
    // -----------------------------------------------------------------------
    // Log-level configuration
    // -----------------------------------------------------------------------

    /**
     * Scans the argument list for an optional log-level token and, if found,
     * overrides the root Logback logger level programmatically.
     *
     * <p>Two syntaxes are accepted (case-insensitive, position-independent):
     * <pre>
     *   --log=DEBUG          named flag, any position
     *   DEBUG                bare word in arg[4]
     * </pre>
     *
     * Valid level names: {@code TRACE DEBUG INFO WARN ERROR OFF}
     * The default level (INFO) is declared in {@code logback.xml}.
     *
     * @param args the raw {@code main} argument array
     */
    private static void applyLogLevel(String[] args) {
        String levelName = null;

        // Check every arg for --log=<LEVEL>
        for (String arg : args) {
            if (arg != null && arg.toLowerCase().startsWith("--log=")) {
                levelName = arg.substring("--log=".length()).trim();
                break;
            }
        }

        // Fall back to positional arg[4]
        if (levelName == null && args.length > 4) {
            levelName = args[4].trim();
        }

        if (levelName == null || levelName.isBlank()) {
            return; // nothing to override — logback.xml default applies
        }

        Level level = Level.toLevel(levelName, null);
        if (level == null) {
            // Use System.out because the logger isn't configured yet
            System.err.println("[WARN] Unknown log level '" + levelName
                    + "' — keeping default. Valid values: TRACE DEBUG INFO WARN ERROR OFF");
            return;
        }

        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(ROOT_LOGGER).setLevel(level);

        // Announce the override on System.out so it is always visible
        log.info("[LOG] Root log level set to " + level + " (from argument)");
    }
    // -----------------------------------------------------------------------
    // Configuration loading
    // -----------------------------------------------------------------------

    /**
     * Immutable holder for the four required runtime parameters.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Command-line arguments (positional arg[0..3])</li>
     *   <li>Bundled {@code cfec.properties} on the classpath</li>
     * </ol>
     */
    public record Config(String cfec, String safe, String username, String password) {

        public static CFECClient.Config load(String[] args) {

            // Strip any --log= flags before treating args as positional
            String[] positional = Arrays.stream(args)
                    .filter(a -> a != null && !a.toLowerCase().startsWith("--log="))
                    .toArray(String[]::new);

            // --- Step 1: read bundled properties (lowest priority) -----------
            Properties props = new Properties();
            try (InputStream in = VaultFolderClient.class
                    .getResourceAsStream(PROPERTIES_RESOURCE)) {
                if (in != null) {
                    props.load(in);
                    log.info("Loaded properties from classpath: {}", PROPERTIES_RESOURCE);
                } else {
                    log.warn("No classpath resource found at {} — relying on command-line args.",
                            PROPERTIES_RESOURCE);
                }
            } catch (Exception e) {
                log.warn("Could not read {}: {}", PROPERTIES_RESOURCE, e.getMessage());
            }

            // --- Step 2: resolve (arg overrides property) --------------------
            String cfec     = resolve("cfec",     argAt(positional, 0), props.getProperty("cfec.vault"));
            String safe     = resolve("safe",     argAt(positional, 1), props.getProperty("cfec.safe"));
            String username = resolve("username", argAt(positional, 2), props.getProperty("cfec.username"));
            String password = resolve("password", argAt(positional, 3), props.getProperty("cfec.password"));

            // --- Step 3: fail fast if anything is missing --------------------
            List<String> missing = new ArrayList<>();
            if (cfec     == null) missing.add("cfec     (arg[0] or cfec.vault in properties)");
            if (safe     == null) missing.add("safe     (arg[1] or cfec.safe in properties)");
            if (username == null) missing.add("username (arg[2] or cfec.username in properties)");
            if (password == null) missing.add("password (arg[3] or cfec.password in properties)");

            if (!missing.isEmpty()) {
                String msg = "Missing required configuration parameter(s):\n  - "
                        + String.join("\n  - ", missing);
                log.error(msg);
                throw new IllegalStateException(msg);
            }

            return new CFECClient.Config(cfec, safe, username, password);
        }

        private static String argAt(String[] args, int index) {
            return (args != null && args.length > index) ? args[index] : null;
        }

        private static String resolve(String key, String fromArgs, String fromProps) {
            if (fromArgs != null && !fromArgs.isBlank()) {
                log.debug("Config key '{}' resolved from command-line argument", key);
                return fromArgs.trim();
            }
            if (fromProps != null && !fromProps.isBlank()) {
                log.debug("Config key '{}' resolved from cfec.properties", key);
                return fromProps.trim();
            }
            return null;
        }
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
        log.info("Calling API: URL: post " + url + "\nwith "+body);
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
    public static ApiResponse listFolders(
            String cfec,
            String safe,
            String myFolderName,
            String sessionCookie) throws Exception {

        String url = String.format("%s/vaults/%s/safes/%s/plan/%s",
                BASE_URL, cfec, safe, myFolderName);
        log.info("Calling API: URL: get " + url);
        return get(url, sessionCookie);
    }

    // -----------------------------------------------------------------------
    // Call 2 — Create folder
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
        log.info("Calling API: URL: post " + url + "\nwith "+body);
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
    // Shared HTTP GET — mirrors post() for GET requests
    // -----------------------------------------------------------------------
    /**
     * Executes an HTTP GET request and returns a fully populated
     * {@link ApiResponse}.
     *
     * @param url           absolute URL to GET
     * @param sessionCookie optional {@code "JSESSIONID=…"} value forwarded as
     *                      the {@code Cookie} header; {@code null} to omit
     * @return {@link ApiResponse} with status code, headers, cookies and body
     */
    private static ApiResponse get(
            String url,
            String sessionCookie) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET();

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
    private static void logResponse(ApiResponse response) {
        log.info("\n--- HTTP Status ---");
        log.info("Status code:"+response.statusCode());

        log.debug("\n--- Response Headers ---");
        response.headers().forEach((name, values) ->
                values.forEach(v -> log.debug(name + ": " + v)));

        log.debug("\n--- Cookies (from Set-Cookie) ---");
        if (response.cookies().isEmpty()) {
            log.debug("(none)");
        } else {
            response.cookies().forEach(c ->
                    log.debug(c.name() + " = " + c.value()
                            + (c.maxAge()  != null ? "  (Max-Age=" + c.maxAge() + ")"  : "")
                            + (c.expires() != null ? "  (Expires=" + c.expires() + ")" : "")));
        }

        log.info("\n--- JSON Body ---");
        log.info(response.body());
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