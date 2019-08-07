package BFT.network.netty;

import BFT.util.Role;

import java.util.EnumMap;
import java.util.Vector;

public class RoleMap {

    protected EnumMap<Role, Vector<String>> map;

    private static RoleMap activeMap;

    public RoleMap(int clients, int filters, int orders, int execs, int verifiers) {
        map = new EnumMap(Role.class);

        int i = 0;
        Vector tmp;
        tmp = new Vector<String>(clients);
        for (i = 0; i < clients; i++)
            tmp.add(i, "CLIENT." + i);
        map.put(Role.CLIENT, tmp);

        tmp = new Vector<String>(execs);
        for (i = 0; i < execs; i++)
            tmp.add(i, "EXEC." + i);
        map.put(Role.EXEC, tmp);

        tmp = new Vector<String>(orders);
        for (i = 0; i < orders; i++)
            tmp.add(i, "ORDER." + i);
        map.put(Role.ORDER, tmp);

        tmp = new Vector<String>(filters);
        for (i = 0; i < filters; i++)
            tmp.add(i, "FILTER." + i);
        map.put(Role.FILTER, tmp);
        tmp = new Vector<String>(verifiers);
        for (i = 0; i < verifiers; i++)
            tmp.add(i, "VERIFIER." + i);
        map.put(Role.VERIFIER, tmp);
    }

    public String getString(Role role, int id) {
        return map.get(role).elementAt(id);
    }


    public static void initialize(int c, int f, int o, int e, int v) {
        if (activeMap == null)
            activeMap = new RoleMap(c, f, o, e, v);
    }

    public static String getRoleString(Role role, int id) {
        return activeMap.getString(role, id);
    }


    public static void main(String arg[]) {
        initialize(4, 3, 2, 1, 2);
        System.out.println(getRoleString(Role.EXEC, 0));
        System.out.println(getRoleString(Role.ORDER, 1));
        System.out.println(getRoleString(Role.FILTER, 2));
        System.out.println(getRoleString(Role.CLIENT, 1));
        System.out.println(getRoleString(Role.VERIFIER, 1));
    }


}