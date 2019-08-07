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

package Applications.jetty.deploy.graph;

/**
 * Basic Graph Edge
 */
public final class Edge {
    private Node _from;
    private Node _to;

    public Edge(Node from, Node to) {
        if (from == null || to == null || from == to)
            throw new IllegalArgumentException("from " + from + " to " + to);
        _from = from;
        _to = to;
    }

    @Override
    public int hashCode() {
        return _from.hashCode() ^ _to.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Edge other = (Edge) obj;
        if (_from == null) {
            if (other._from != null)
                return false;
        } else if (!_from.equals(other._from))
            return false;
        if (_to == null) {
            if (other._to != null)
                return false;
        } else if (!_to.equals(other._to))
            return false;
        return true;
    }

    public Node getFrom() {
        return _from;
    }

    public Node getTo() {
        return _to;
    }

    @Override
    public String toString() {
        return _from + "->" + _to;
    }
}