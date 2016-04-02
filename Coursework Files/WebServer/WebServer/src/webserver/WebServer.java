package webserver;

import in2011.http.Request;
import in2011.http.Response;
import in2011.http.StatusCodes;
import in2011.http.EmptyMessageException;
import in2011.http.MessageFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.*;
import java.util.Date;
import org.apache.http.client.utils.DateUtils;

public class WebServer implements Runnable {

    private final int port;
    private final String rootDir;

    public WebServer(int port, String rootDir) {
        this.port = port;
        this.rootDir = rootDir;
    }

    public void start() throws IOException {
        
        while (true) {
            
            ServerSocket serverSock = new ServerSocket(port);
            
            Socket conn = serverSock.accept();
            
            InputStream is = conn.getInputStream();

                try {
                    Request req = Request.parse(is);
                } catch (MessageFormatException ex) {}

                OutputStream os = conn.getOutputStream();

                Response msg = new Response(200);
                msg.write(os);
                os.write("This is a test message".getBytes());

                conn.close();
        }
    }

    public static void main(String[] args) throws IOException {
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
        WebServer server = new WebServer(port, rootDir);
        server.start();
    }

    @Override
    public void run() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
