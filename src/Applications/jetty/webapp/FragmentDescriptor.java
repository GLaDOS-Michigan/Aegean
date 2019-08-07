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

package Applications.jetty.webapp;

import Applications.jetty.util.resource.Resource;
import Applications.jetty.xml.XmlParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


/**
 * Fragment
 * <p>
 * A web-fragment.xml descriptor.
 */
public class FragmentDescriptor extends WebDescriptor {
    public static final String NAMELESS = "@@-NAMELESS-@@"; //prefix for nameless Fragments
    protected static int _counter = 0;

    public enum OtherType {None, Before, After}

    ;
    protected OtherType _otherType = OtherType.None;


    protected List<String> _befores = new ArrayList<String>();
    protected List<String> _afters = new ArrayList<String>();
    protected String _name;


    public FragmentDescriptor(Resource xml)
            throws Exception {
        super(xml);
    }

    public String getName() {
        return _name;
    }

    @Override
    public void parse()
            throws Exception {
        super.parse();
        processName();
    }

    public void processName() {
        XmlParser.Node root = getRoot();
        XmlParser.Node nameNode = root.get("name");
        _name = NAMELESS + (_counter++);
        if (nameNode != null) {
            String tmp = nameNode.toString(false, true);
            if (tmp != null && tmp.length() > 0)
                _name = tmp;
        }
    }

    @Override
    public void processOrdering() {
        //Process a fragment jar's web-fragment.xml<ordering> elements
        XmlParser.Node root = getRoot();

        XmlParser.Node ordering = root.get("ordering");
        if (ordering == null)
            return; //No ordering for this fragment

        _isOrdered = true;

        processBefores(ordering);
        processAfters(ordering);
    }


    public void processBefores(XmlParser.Node ordering) {
        //Process the <before> elements, looking for an <others/> clause and all of the <name> clauses
        XmlParser.Node before = ordering.get("before");
        if (before == null)
            return;

        Iterator<?> iter = before.iterator();
        XmlParser.Node node = null;
        while (iter.hasNext()) {
            Object o = iter.next();
            if (!(o instanceof XmlParser.Node)) continue;
            node = (XmlParser.Node) o;
            if (node.getTag().equalsIgnoreCase("others")) {
                if (_otherType != OtherType.None)
                    throw new IllegalStateException("Duplicate <other> clause detected in " + _xml.getURI());

                _otherType = OtherType.Before;
            } else if (node.getTag().equalsIgnoreCase("name"))
                _befores.add(node.toString(false, true));
        }
    }

    public void processAfters(XmlParser.Node ordering) {
        //Process the <after> elements, look for an <others/> clause and all of the <name/> clauses
        XmlParser.Node after = ordering.get("after");
        if (after == null)
            return;

        Iterator<?> iter = after.iterator();
        XmlParser.Node node = null;
        while (iter.hasNext()) {
            Object o = iter.next();
            if (!(o instanceof XmlParser.Node)) continue;
            node = (XmlParser.Node) o;
            if (node.getTag().equalsIgnoreCase("others")) {
                if (_otherType != OtherType.None)
                    throw new IllegalStateException("Duplicate <other> clause detected in " + _xml.getURI());

                _otherType = OtherType.After;

            } else if (node.getTag().equalsIgnoreCase("name"))
                _afters.add(node.toString(false, true));
        }
    }

    public List<String> getBefores() {
        return Collections.unmodifiableList(_befores);
    }

    public List<String> getAfters() {
        return Collections.unmodifiableList(_afters);
    }

    public OtherType getOtherType() {
        return _otherType;
    }

    public List<String> getOrdering() {
        return null; //only used for absolute-ordering in Descriptor
    }
}
