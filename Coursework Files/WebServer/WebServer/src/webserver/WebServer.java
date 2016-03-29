package webserver;

import in2011.http.Request;
import in2011.http.Response;
import in2011.http.StatusCodes;
import in2011.http.EmptyMessageException;
import in2011.http.MessageFormatException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;

public final class WebServer implements Runnable {

    // Constants
    private int START_CLOSING_AT = 10;
    private final int CLOSE_EVERY_MILLISEC = 1000;

    // Static variables keeping track of threads
    public static int counter;
    public static ArrayList<WebServer> servers = new ArrayList<>();

    private Thread t;
    private final int port;
    private final String rootDir;
    private final int number;
    private ServerSocket serverSocket;
    private Timer timer;
    public boolean active = true;

    // Called for the first thread, never again
    public WebServer(int port, String rootDir) throws InterruptedException, IOException {
        this.port = port;
        this.rootDir = rootDir;

        // If servers size more than startClosingConnectionsAt, close the one opened the last
        ActionListener action = (ActionEvent e) -> {
            if (servers.size() > START_CLOSING_AT) {
                System.out.println("Size is " + servers.size() + ", closing one connection");
                WebServer last = servers.get(servers.size() - 1);
                last.active = false;
                servers.remove(last);
            }
        };

        servers.add(this);
        counter++;
        number = counter;
        System.out.println("New WebServer : " + number);

        // Start connection closing timer
        timer = new Timer(CLOSE_EVERY_MILLISEC, action);
        timer.start();

        handler();
    }

    // Called for new Thread, takes original ServerSocket
    public WebServer(int port, String rootDir, ServerSocket serverSocket) throws InterruptedException {
        this.port = port;
        this.rootDir = rootDir;
        this.serverSocket = serverSocket;

        servers.add(this);

        counter++;
        number = counter;

        System.out.println("New WebServer : " + number);
    }

    // Directs WebServer to correct startConnection
    private void handler() throws InterruptedException, IOException {
        if (serverSocket == null) {
            startConnection();
        } else {
            startConnection(serverSocket);
        }
    }

    // First connection - Creates ServerSocket
    private void startConnection() throws InterruptedException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            String myURI;
            File file;
            while (active) {
                String content = "";
                System.out.println(number + " is Awaiting Connection...");
                try (Socket connection = serverSocket.accept()) {
                    // Open new thread
                    startThread(port, rootDir, serverSocket);

                    System.out.println(number + " Connected");
                    // Handle this request
                    InputStream inStream = connection.getInputStream();
                    PrintWriter out = new PrintWriter(connection.getOutputStream(), true);
                    Request msg = Request.parse(inStream);
                    myURI = msg.getURI();
                    if (!"GET".equals(msg.getMethod())) {
                        sendResponse(out, "", 501, connection);
                        continue;
                    }
                    if (Float.parseFloat(msg.getVersion()) > 1.1f) {
                        sendResponse(out, "", 505, connection);
                        continue;
                    }
                    String ps = rootDir + myURI;
                    if (myURI.contains("favicon")) {
                        connection.close();
                        continue;
                    }
                    Path p = Paths.get(ps).toAbsolutePath().normalize();
                    file = p.toFile();
                    if (!file.exists()) {
                        sendResponse(out, "", 404, connection);
                        continue;
                    }
                    FileReader reader = new FileReader(file);
                    BufferedReader bufferedReader = new BufferedReader(reader);
                    String line;
                    while ((content += (line = bufferedReader.readLine())) == null) {
                        content += line;
                    }
                    sendResponse(out, content, 200, connection);
                } catch (MessageFormatException ex) {
                    Logger.getLogger(WebServer.class.getName()).log(Level.SEVERE, null, ex);
                } catch (java.nio.file.InvalidPathException inv) {
                    System.out.println("URL is invalid! - Restarting");
                    startConnection();
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(WebServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // Every other connection - Takes serversocket as parameter
    private void startConnection(ServerSocket socket) throws InterruptedException, IOException {
        String myURI;
        File file;
        while (true) {
            String content = "";
            System.out.println(number + " is Awaiting Connection...");
            try (Socket connection = socket.accept()) {
                // Open new thread
                startThread(port, rootDir, socket);

                System.out.println(number + " Connected");
                // Handle this request
                InputStream inStream = connection.getInputStream();
                PrintWriter out = new PrintWriter(connection.getOutputStream(), true);
                Request msg = Request.parse(inStream);
                myURI = msg.getURI();
                if (!"GET".equals(msg.getMethod())) {
                    sendResponse(out, "", 501, connection);
                    continue;
                }
                if (Float.parseFloat(msg.getVersion()) > 1.1f) {
                    sendResponse(out, "", 505, connection);
                    continue;
                }
                String ps = rootDir + myURI;
                if (myURI.contains("favicon")) {
                    // Prevents reopening same connection
                    Thread.sleep(1000);
                    continue;
                }
                Path p = Paths.get(ps).toAbsolutePath().normalize();
                file = p.toFile();
                if (!file.exists()) {
                    sendResponse(out, "", 404, connection);
                    continue;
                }
                FileReader reader = new FileReader(file);
                BufferedReader bufferedReader = new BufferedReader(reader);
                String line;
                while ((content += (line = bufferedReader.readLine())) == null) {
                    content += line;
                }
                sendResponse(out, content, 200, connection);
            } catch (MessageFormatException ex) {
                Logger.getLogger(WebServer.class.getName()).log(Level.SEVERE, null, ex);
            } catch (java.nio.file.InvalidPathException inv) {
                System.out.println("URL is invalid! - Restarting");
                startConnection();
            }
        }
    }

    // Send response with response code
    private void sendResponse(PrintWriter out, String content, int type, Socket connection) throws IOException, InterruptedException {
        Response response = new Response(type);
        out.println(response + "\n" + content);
        System.out.println(number + " reponded");
        // Prevents reopening same connection
        Thread.sleep(1000);
    }

    // overriden run from Runnable to call handler function for every thread that isnt the first
    @Override
    public void run() {
        try {
            handler();
        } catch (InterruptedException | IOException ex) {
            Logger.getLogger(WebServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // Initiates a new Thread
    public void startThread(int port, String rootDir, ServerSocket serverSocket) throws InterruptedException {
        if (t == null) {
            t = new Thread(new WebServer(port, rootDir, serverSocket), "Thread");
            t.start();
        }
    }

    public static void main(String[] args) throws IOException, MessageFormatException, InterruptedException {
        String usage = "Usage: java webserver.WebServer <port-number> <root-dir>";
        if (args.length != 2) {
            throw new Error(usage);
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new Error(usage + "\n" + "<port-number> must be an integer");
        }
        String rootDir = args[1];
        // Start first connection
        WebServer webServer = new WebServer(port, rootDir);
    }
}
