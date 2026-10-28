package duct.main.nrepl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Eval {
    static final int EXIT_SUCCESS      = 0;
    static final int EXIT_EVAL_ERROR   = 1;
    static final int EXIT_CLIENT_ERROR = 2;
    static final int EXIT_IO_ERROR     = 3;
    static final int EXIT_TIMEOUT      = 124;

    static final int DEFAULT_TIMEOUT_SECS = 60;
    static final int DEFAULT_MAX_CHARS    = 20000;
    static final int GRACE_PERIOD_MS      = 5000;
    static final int WAIT_INTERVAL_MS     = 100;

    // Evaluated after an error to print the cause and ex-data of *e as edn.
    // Integrant's :system key is removed, as it's too large
    static final String ERROR_DETAILS_CODE =
        "(binding [*print-length* 10, *print-level* 8]" +
        "  (prn (-> (Throwable->map *e)" +
        "           (dissoc :trace :data)" +
        "           (update :via (partial mapv #(cond-> % (:data %)" +
        "                                         (update :data dissoc " +
        "                                                 :function :system)))))))";

    static class Config {
        Integer port;
        String code;
        int timeout;
        int maxChars;
        int wait;

        Config(Integer port, String code, int timeout, int maxChars, int wait) {
            this.port = port;
            this.code = code;
            this.timeout = timeout;
            this.maxChars = maxChars;
            this.wait = wait;
        }
    }

    public static void main(String[] args) {
        try {
            System.exit(run(parseArguments(args)));
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(EXIT_IO_ERROR);
        }
    }

    private static int parseInt(String num, String name, int lower, int upper) {
        try {
            int n = Integer.parseInt(num);
            if (n >= lower && n <= upper) return n;
        } catch (NumberFormatException e) {
        }
        System.err.println("Invalid " + name + ": " + num);
        System.exit(EXIT_CLIENT_ERROR);
        return 0;
    }

    private static Integer readPortFile() throws IOException {
        Path path = Paths.get(".nrepl-port");
        if (!Files.exists(path)) return null;

        // The file may exist before the port has been written to it.
        String port = Files.readString(path).trim();
        return port.isEmpty() ? null : parsePort(port);
    }

    private static int parsePort(String port) {
        return parseInt(port, "port", 1, 65535);
    }

    private static int parseTimeout(String timeout) {
        return parseInt(timeout, "timeout", 1, Integer.MAX_VALUE);
    }

    private static int parseMaxChars(String maxChars) {
        return parseInt(maxChars, "max chars", 1, Integer.MAX_VALUE);
    }

    private static int parseWait(String wait) {
        return parseInt(wait, "wait", 1, Integer.MAX_VALUE);
    }

    private static String readCode(String code) throws IOException {
        return code.equals("-") ? readStdin() : code;
    }

    private static String readStdin() throws IOException {
        return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String requireValue(String[] args, int i) {
        if (i >= args.length) {
            exitWithUsage("Missing value for " + args[i - 1]);
        }
        return args[i];
    }

    private static void exitWithUsage(String message) {
        System.err.println(message);
        System.err.println("Usage: duct.main.nrepl.Eval [--port PORT] " +
                           "[--timeout SECS] [--max-chars N] [--wait SECS] " +
                           "[CODE | -]");
        System.exit(EXIT_CLIENT_ERROR);
    }

    private static Config parseArguments(String[] args) throws IOException {
        String port = null;
        String code = null;
        int timeout = DEFAULT_TIMEOUT_SECS;
        int maxChars = DEFAULT_MAX_CHARS;
        int wait = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    port = requireValue(args, ++i);
                    break;
                case "--timeout":
                    timeout = parseTimeout(requireValue(args, ++i));
                    break;
                case "--max-chars":
                    maxChars = parseMaxChars(requireValue(args, ++i));
                    break;
                case "--wait":
                    wait = parseWait(requireValue(args, ++i));
                    break;
                default:
                    if (args[i].startsWith("--")) {
                        exitWithUsage("Unknown option: " + args[i]);
                    }
                    if (code != null) {
                        exitWithUsage("Unexpected argument: " + args[i]);
                    }
                    code = args[i];
            }
        }

        return new Config(port == null ? null : parsePort(port),
                          code == null ? readStdin() : readCode(code),
                          timeout, maxChars, wait);
    }

    private static int run(Config arguments) throws IOException {
        try (Socket socket = connect(arguments.port, arguments.wait)) {
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            InputStream in   = new BufferedInputStream(socket.getInputStream());

            String session = cloneSession(out, in);
            try {
                int exitCode = eval(socket, out, in, session, arguments.code,
                                    arguments.timeout, arguments.maxChars);
                if (exitCode == EXIT_EVAL_ERROR) {
                    socket.setSoTimeout(GRACE_PERIOD_MS);
                    printErrorDetails(out, in, session);
                }
                return exitCode;
            } finally {
                socket.setSoTimeout(GRACE_PERIOD_MS);
                closeSession(out, in, session);
            }
        }
    }

    private static Socket connect(Integer port, int wait) throws IOException {
        long deadline = System.nanoTime() + wait * 1_000_000_000L;
        while (true) {
            Integer currentPort = port != null ? port : readPortFile();
            if (currentPort != null) {
                try {
                    return new Socket("localhost", currentPort);
                } catch (ConnectException e) {
                    if (wait == 0) {
                        printConnectError(currentPort, port == null);
                        System.exit(EXIT_IO_ERROR);
                    }
                }
            } else if (wait == 0) {
                System.err.println("No .nrepl-port file found; use --port PORT");
                System.exit(EXIT_CLIENT_ERROR);
            }
            if (System.nanoTime() >= deadline) {
                System.err.println("Timed out waiting for nREPL server after " +
                                   wait + "s");
                System.exit(EXIT_CLIENT_ERROR);
            }
            try {
                Thread.sleep(WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void printConnectError(int port, boolean fromPortFile) {
        System.err.println("Could not connect to nREPL server on port " + port);
        if (fromPortFile) {
            System.err.println("The .nrepl-port file may be left over from a " +
                               "server that has stopped");
        } else {
            System.err.println("If the server is still starting, use --wait SECS");
        }
    }

    private static int eval(Socket socket, OutputStream out, InputStream in,
                            String session, String code, int timeout,
                            int maxChars) throws IOException {
        Map<String, Object> request = new TreeMap<>();
        request.put("op", "eval");
        request.put("session", session);
        request.put("code", code);
        request.put("id", "1");
        request.put("nrepl.middleware.print/quota", maxChars);
        writeBencode(out, request);
        out.flush();

        long deadline = System.nanoTime() + timeout * 1_000_000_000L;
        int exitCode = EXIT_SUCCESS;
        while (true) {
            Map<?, ?> response;
            try {
                long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
                socket.setSoTimeout((int) Math.max(1, remainingMs));
                response = (Map<?, ?>) readBencode(in);
            } catch (SocketTimeoutException e) {
                System.err.println("Evaluation timed out after " + timeout + "s");
                interrupt(socket, out, in, session);
                System.err.println("Evaluation was interrupted, but if it ignores " +
                                   "interrupts it may still be running on the server");
                return EXIT_TIMEOUT;
            }
            printString(response.get("out"), System.out);
            printString(response.get("err"), System.err);
            if (response.containsKey("value")) {
                System.out.println(string(response.get("value")));
            }
            Object status = response.get("status");
            if (hasStatus(status, "nrepl.middleware.print/truncated")) {
                System.err.println("Value truncated to " + maxChars +
                                   " characters; use --max-chars to change the limit");
            }
            if (hasStatus(status, "eval-error")) {
                exitCode = EXIT_EVAL_ERROR;
            }
            if (hasStatus(status, "need-input")) {
                System.err.println("Evaluation is waiting for input from *in*, " +
                                   "which this client doesn't support");
                interrupt(socket, out, in, session);
                return EXIT_CLIENT_ERROR;
            }
            if (hasStatus(status, "done")) {
                return exitCode;
            }
        }
    }

    private static void printErrorDetails(OutputStream out, InputStream in,
                                          String session) throws IOException {
        Map<String, Object> request = new TreeMap<>();
        request.put("op", "eval");
        request.put("session", session);
        request.put("code", ERROR_DETAILS_CODE);
        request.put("id", "3");
        writeBencode(out, request);
        out.flush();

        try {
            while (true) {
                Map<?, ?> response = (Map<?, ?>) readBencode(in);
                printString(response.get("out"), System.err);
                printString(response.get("err"), System.err);
                if (hasStatus(response.get("status"), "done")) {
                    return;
                }
            }
        } catch (SocketTimeoutException e) {
            // The details are optional; give up if they take too long.
        }
    }

    private static String cloneSession(OutputStream out, InputStream in)
        throws IOException {
        Map<String, Object> request = new TreeMap<>();
        request.put("op", "clone");
        request.put("id", "0");
        writeBencode(out, request);
        out.flush();

        Map<?, ?> response = (Map<?, ?>) readBencode(in);
        return string(response.get("new-session"));
    }

    private static void closeSession(OutputStream out, InputStream in,
                                     String session) throws IOException {
        Map<String, Object> request = new TreeMap<>();
        request.put("op", "close");
        request.put("session", session);
        request.put("id", "2");
        writeBencode(out, request);
        out.flush();

        try {
            waitForDone(in, "2");
        } catch (SocketTimeoutException e) {
            // The server didn't confirm the close; give up waiting.
        }
    }

    private static void interrupt(Socket socket, OutputStream out, InputStream in,
                                  String session) throws IOException {
        Map<String, Object> request = new TreeMap<>();
        request.put("op", "interrupt");
        request.put("session", session);
        request.put("interrupt-id", "1");
        writeBencode(out, request);
        out.flush();

        try {
            socket.setSoTimeout(GRACE_PERIOD_MS);
            waitForDone(in, "1");
        } catch (SocketTimeoutException e) {
            System.err.println("Evaluation did not stop after being interrupted, " +
                               "and may still be running on the server");
        }
    }

    private static void waitForDone(InputStream in, String id) throws IOException {
        while (true) {
            Map<?, ?> response = (Map<?, ?>) readBencode(in);
            Object responseId = response.get("id");
            if (responseId != null && id.equals(string(responseId)) &&
                hasStatus(response.get("status"), "done")) {
                return;
            }
        }
    }

    private static boolean hasStatus(Object status, String value) {
        if (status instanceof List) {
            for (Object s : (List<?>) status) {
                if (value.equals(string(s))) return true;
            }
        }
        return false;
    }

    private static void printString(Object bytes, java.io.PrintStream stream) {
        if (bytes != null) {
            stream.print(string(bytes));
            stream.flush();
        }
    }

    private static String string(Object bytes) {
        return new String((byte[]) bytes, StandardCharsets.UTF_8);
    }

    private static void writeBencode(OutputStream out, Object value)
        throws IOException {
        if (value instanceof String) {
            byte[] bytes = ((String) value).getBytes(StandardCharsets.UTF_8);
            out.write((bytes.length + ":").getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
        } else if (value instanceof Integer) {
            out.write(("i" + value + "e").getBytes(StandardCharsets.US_ASCII));
        } else if (value instanceof Map) {
            out.write('d');
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                writeBencode(out, e.getKey());
                writeBencode(out, e.getValue());
            }
            out.write('e');
        } else {
            throw new IllegalArgumentException("Cannot bencode: " + value);
        }
    }

    private static Object readBencode(InputStream in) throws IOException {
        int c = readByte(in);
        switch (c) {
            case 'i':
                return Long.parseLong(readUntil(in, 'e'));
            case 'l': {
                List<Object> list = new ArrayList<>();
                while (true) {
                    in.mark(1);
                    if (readByte(in) == 'e') return list;
                    in.reset();
                    list.add(readBencode(in));
                }
            }
            case 'd': {
                Map<String, Object> map = new TreeMap<>();
                while (true) {
                    in.mark(1);
                    if (readByte(in) == 'e') return map;
                    in.reset();
                    map.put(string(readBencode(in)), readBencode(in));
                }
            }
            default: {
                // Bencode strings are returned as byte arrays
                int length = Integer.parseInt((char) c + readUntil(in, ':'));
                byte[] bytes = in.readNBytes(length);
                if (bytes.length < length) throw new EOFException();
                return bytes;
            }
        }
    }

    private static String readUntil(InputStream in, char end) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c;
        while ((c = readByte(in)) != end) buf.write(c);
        return buf.toString(StandardCharsets.US_ASCII);
    }

    private static int readByte(InputStream in) throws IOException {
        int c = in.read();
        if (c == -1) throw new EOFException();
        return c;
    }
}
