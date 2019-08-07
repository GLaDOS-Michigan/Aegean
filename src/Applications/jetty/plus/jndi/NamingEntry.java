//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package Applications.jetty.plus.jndi;

import Applications.jetty.jndi.NamingUtil;
import Applications.jetty.util.log.Logger;

import javax.naming.*;


/**
 * NamingEntry
 * <p>
 * Base class for all jndi related entities. Instances of
 * subclasses of this class are declared in jetty.xml or in a
 * webapp's WEB-INF/jetty-env.xml file.
 * <p>
 * NOTE: that all NamingEntries will be bound in a single namespace.
 * The "global" level is just in the top level context. The "local"
 * level is a context specific to a webapp.
 */
public abstract class NamingEntry {
    private static Logger __log = NamingUtil.__log;
    public static final String __contextName = "__"; //all NamingEntries stored in context called "__"
    protected final Object _scope;
    protected final String _jndiName;  //the name representing the object associated with the NamingEntry
    protected String _namingEntryNameString; //the name of the NamingEntry relative to the context it is stored in
    protected String _objectNameString; //the name of the object relative to the context it is stored in


    public String toString() {
        return _jndiName;
    }


    protected NamingEntry(Object scope, String jndiName)
            throws NamingException {
        this._scope = scope;
        this._jndiName = jndiName;
    }

    /**
     * Create a NamingEntry.
     * A NamingEntry is a name associated with a value which can later
     * be looked up in JNDI by a webapp.
     * <p>
     * We create the NamingEntry and put it into JNDI where it can
     * be linked to the webapp's env-entry, resource-ref etc entries.
     *
     * @param jndiName the name of the object which will eventually be in java:comp/env
     * @throws NamingException
     */
    protected NamingEntry(String jndiName)
            throws NamingException {
        this(null, jndiName);
    }


    /**
     * Add a java:comp/env binding for the object represented by this NamingEntry,
     * but bind it as the name supplied
     *
     * @throws NamingException
     */
    public void bindToENC(String localName)
            throws NamingException {
        //TODO - check on the whole overriding/non-overriding thing
        InitialContext ic = new InitialContext();
        Context env = (Context) ic.lookup("java:comp/env");
        __log.debug("Binding java:comp/env/" + localName + " to " + _objectNameString);
        NamingUtil.bind(env, localName, new LinkRef(_objectNameString));
    }

    /**
     * Unbind this NamingEntry from a java:comp/env
     */
    public void unbindENC() {
        try {
            InitialContext ic = new InitialContext();
            Context env = (Context) ic.lookup("java:comp/env");
            __log.debug("Unbinding java:comp/env/" + getJndiName());
            env.unbind(getJndiName());
        } catch (NamingException e) {
            __log.warn(e);
        }
    }

    /**
     * Unbind this NamingEntry entirely
     */
    public void release() {
        try {
            InitialContext ic = new InitialContext();
            ic.unbind(_objectNameString);
            ic.unbind(_namingEntryNameString);
            this._namingEntryNameString = null;
            this._objectNameString = null;
        } catch (NamingException e) {
            __log.warn(e);
        }
    }

    /**
     * Get the unique name of the object
     * relative to the scope
     *
     * @return the unique jndi name of the object
     */
    public String getJndiName() {
        return _jndiName;
    }

    /**
     * Get the name of the object, fully
     * qualified with the scope
     *
     * @return the name of the object, fully qualified with the scope
     */
    public String getJndiNameInScope() {
        return _objectNameString;
    }


    /**
     * Save the NamingEntry for later use.
     * <p>
     * Saving is done by binding the NamingEntry
     * itself, and the value it represents into
     * JNDI. In this way, we can link to the
     * value it represents later, but also
     * still retrieve the NamingEntry itself too.
     * <p>
     * The object is bound at the jndiName passed in.
     * This NamingEntry is bound at __/jndiName.
     * <p>
     * eg
     * <p>
     * jdbc/foo    : DataSource
     * __/jdbc/foo : NamingEntry
     *
     * @throws NamingException
     */
    protected void save(Object object)
            throws NamingException {
        __log.debug("SAVE {} in {}", this, _scope);
        InitialContext ic = new InitialContext();
        NameParser parser = ic.getNameParser("");
        Name prefix = NamingEntryUtil.getNameForScope(_scope);

        //bind the NamingEntry into the context
        Name namingEntryName = NamingEntryUtil.makeNamingEntryName(parser, getJndiName());
        namingEntryName.addAll(0, prefix);
        _namingEntryNameString = namingEntryName.toString();
        NamingUtil.bind(ic, _namingEntryNameString, this);

        //bind the object as well
        Name objectName = parser.parse(getJndiName());
        objectName.addAll(0, prefix);
        _objectNameString = objectName.toString();
        NamingUtil.bind(ic, _objectNameString, object);
    }

}
