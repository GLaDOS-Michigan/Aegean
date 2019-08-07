package Applications.web;

import BFT.exec.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

public class WebProxy implements RequestHandler, RequestFilter, RequestHandlerPlus {

    private ReplyHandler replyHandler;
    private int id;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage Server <id> <membership>");
            return;
        }

        ExecBaseNode exec = new ExecBaseNode(args[1], Integer.parseInt(args[0]));
        WebProxy main = new WebProxy(exec, Integer.parseInt(args[0]));
        exec.start(main, main);
    }

    public WebProxy(ReplyHandler replyHandler, int id) {
        this.replyHandler = replyHandler;
        this.id = id;
    }

    @Override
    public void execReadOnly(byte[] request, RequestInfo info) {
        throw new RuntimeException("Not Implemented");
    }

    public void execRequest(byte[] request, RequestInfo info) {
        StringBuilder builder = new StringBuilder();
        try {
            Socket sock = new Socket("localhost", 8080);
            OutputStream out = sock.getOutputStream();
            out.write(request);
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                builder.append(inputLine).append("\n");
            }
            sock.close();
            replyHandler.result(builder.toString().getBytes(), info);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void execRequests(byte[][] request, RequestInfo[] info) {
        try {
            Socket[] socks = new Socket[request.length];
            for (int i = 0; i < request.length; i++) {
                if (request[i] != null) {
                    socks[i] = new Socket("127.0.0.1", 8080);
                    OutputStream out = socks[i].getOutputStream();
                    out.write(request[i]);
                    out.flush();
                }
            }

            for (int i = 0; i < request.length; i++) {
                if (request[i] != null) {
                    byte[] reply = new byte[10250];
                    int index = 0;
                    InputStream in = socks[i].getInputStream();
                    while (index < 10250) {
                        int count = in.read(reply, index, reply.length - index);
                        if (count <= 0)
                            break;
                        index += count;
                    }
                    socks[i].close();
                    replyHandler.result(reply, info[i]);

                			/*StringBuilder builder = new StringBuilder(10300);
                                BufferedReader in = new BufferedReader(new InputStreamReader(socks[i].getInputStream()));
                        		String inputLine;
                        		while((inputLine = in.readLine()) != null){
                                		builder.append(inputLine).append("\n");
                        		}
                        		socks[i].close();
                        		replyHandler.result(builder.toString().getBytes(), info[i]);*/
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public List<RequestKey> generateKeys(byte[] request) {
        return null;
    }


}
