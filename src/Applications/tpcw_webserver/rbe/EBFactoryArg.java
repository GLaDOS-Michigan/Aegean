/*-------------------------------------------------------------------------
 * rbe.EBFactoryArg.java
 * Timothy Heil
 * 10/29/99
 *
 * Abstract command line argument parsing class.
 *------------------------------------------------------------------------*/

package Applications.tpcw_webserver.rbe;

import Applications.tpcw_webserver.rbe.args.Arg;
import Applications.tpcw_webserver.rbe.args.ArgDB;
import BFT.exec.ExecBaseNode;

import java.util.Vector;

public class EBFactoryArg extends Arg {
    public Vector ebs;
    public RBE rbe;
    public int maxState;
    public String className;
    public String membershipFile;

    public EBFactoryArg(String arg, String name, String desc,
                        RBE rbe, Vector ebs) {
        super(arg, name, desc, true, false);
        this.rbe = rbe;
        this.ebs = ebs;
    }

    public EBFactoryArg(String arg, String name, String desc,
                        RBE rbe, Vector ebs, ArgDB db) {
        super(arg, name, desc, true, false, db);
        this.rbe = rbe;
        this.ebs = ebs;
    }

    // Customize to parse arguments.
    protected int parseMatch(String[] args, int a)
            throws Arg.Exception {
        int num;
        int p;
        int i;

        if (a == args.length) {
            throw new Arg.Exception("Missing factory class name.", a);
        }

        // Read in factory class.
        String factoryClassName = args[a];
        className = factoryClassName;
        EBFactory factory;
        try {
            Class factoryClass = Class.forName(factoryClassName);
            factory = (EBFactory) factoryClass.newInstance();
        } catch (ClassNotFoundException cnf) {
            throw new Arg.Exception("Unable to find factory class " +
                    factoryClassName + ".", a);
        } catch (InstantiationException ie) {
            throw new
                    Arg.Exception("Unable to instantiate factory class " +
                    factoryClassName + ".", a);
        } catch (IllegalAccessException iae) {
            throw new Arg.Exception("Unable to access constructor " +
                    "for factory class " +
                    factoryClassName + ".", a);
        } catch (ClassCastException cce) {
            throw new Arg.Exception("Factory class " + factoryClassName +
                    " is not a subclass of EBFactory.", a);
        }

        //Set the membershipfile
        a++;
        if(a == args.length) {
            throw new Arg.Exception("missing membership files", a);
        }
        String membershipfile = args[a];
        this.membershipFile = membershipfile;

        //Parse starting id
        a++;
        if(a == args.length) {
            throw new Arg.Exception("missing start id", a);
        }
        int idStart = Integer.parseInt(args[a]);

        //Parse end id
        a++;
        if(a == args.length) {
            throw new Arg.Exception("missing end id", a);
        }
        int idEnd = Integer.parseInt(args[a]);

        //Parse number of total operations
        a++;
        if(a == args.length) {
            throw new Arg.Exception("missing totalOps", a);
        }
        int totalOps = Integer.parseInt(args[a]);

        //Parse output directiory
        a++;
        if(a == args.length) {
            throw new Arg.Exception("missing output directory", a);
        }
        String outputDirectory = args[a];

//        // Parse number of EBs to create with this factory.
//        a++;
//        if (a == args.length) {
//            throw new Arg.Exception("Missing factory EB count.", a);
//        }
//        try {
//            num = Integer.parseInt(args[a]);
//        } catch (NumberFormatException nfe) {
//            throw new Arg.Exception("Unable to parse number of EBs.", a);
//        }

        a++;
        p = factory.initialize(args, a);
        if (p == -1) {
            // Factory was unable to parse the input args.
            throw new Arg.
                    Exception("Factory class " + factoryClassName +
                    " unable to parse input arguments.", a);
        }
        a = p;

        // Create EBs
        rbe.setOutputDirectory(outputDirectory);
        for (int id = idStart; id < idEnd; id += 1) {
            factory.setCurrentClientId(id);
            factory.setTotalRequests(totalOps);
            EB e = factory.getEB(rbe, membershipFile);
            if (e.states() > maxState) {
                maxState = e.states();
            }
            ebs.addElement(e);
        }

        return (a);
    }

    public String value() {
        return ("" + ebs.size() + " EBs");
    }

    public void setMembershipFile(String membershipFile) {
        this.membershipFile = membershipFile;
    }
}
