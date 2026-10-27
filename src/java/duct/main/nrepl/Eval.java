package duct.main.nrepl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Eval {
    public static void main(String[] args) throws IOException {
        int port;
        String code;

        if (args.length == 3 && args[0].equals("--port")) {
            port = Integer.parseInt(args[1]);
            code = args[2];
        } else if (args.length == 1) {
            port = readPortFile();
            code = args[0];
        } else {
            System.err.println("Usage: duct.main.nrepl.Eval [--port PORT] CODE");
            System.exit(2);
            return;
        }

        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            InputStream in   = new BufferedInputStream(socket.getInputStream());

            Map<String, Object> request = new TreeMap<>();
            request.put("op", "eval");
            request.put("code", code);
            request.put("id", "1");
            writeBencode(out, request);
            out.flush();

            while (true) {
                Map<?, ?> response = (Map<?, ?>) readBencode(in);
                printString(response.get("out"), System.out);
                printString(response.get("err"), System.err);
                if (response.containsKey("value")) {
                    System.out.println(string(response.get("value")));
                }
                Object status = response.get("status");
                if (status instanceof List && isDone((List<?>) status)) {
                    break;
                }
            }
        }
    }

    private static int readPortFile() throws IOException {
        Path path = Paths.get(".nrepl-port");
        if (!Files.exists(path)) {
            System.err.println("No .nrepl-port file found; use --port PORT");
            System.exit(2);
        }
        return Integer.parseInt(Files.readString(path).trim());
    }

    private static boolean isDone(List<?> status) {
        for (Object s : status) {
            if ("done".equals(string(s))) return true;
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
