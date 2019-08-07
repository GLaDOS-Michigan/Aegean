package Applications.tpcw_servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;

@SuppressWarnings("serial")
public abstract class TPCW_servlet extends HttpServlet {
    public abstract void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException;
}
