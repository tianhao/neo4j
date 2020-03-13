/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.newapi;

import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;

import java.util.function.LongPredicate;

import org.neo4j.internal.kernel.api.CursorFactory;
import org.neo4j.internal.kernel.api.NodeCursor;
import org.neo4j.internal.kernel.api.helpers.RelationshipSelections;
import org.neo4j.io.pagecache.tracing.cursor.PageCursorTracer;
import org.neo4j.kernel.impl.api.KernelTransactionImplementation;
import org.neo4j.kernel.impl.locking.Locks;
import org.neo4j.lock.LockTracer;
import org.neo4j.lock.ResourceTypes;

class DetachingRelationshipDeleter
{
    private final LongPredicate relationshipDeleter;

    DetachingRelationshipDeleter( LongPredicate relationshipDeleter )
    {
        this.relationshipDeleter = relationshipDeleter;
    }

    int lockNodesAndDeleteRelationships( long nodeId, KernelTransactionImplementation ktx )
    {
        Locks.Client locks = ktx.statementLocks().optimistic();
        LockTracer lockTracer = ktx.lockTracer();
        PageCursorTracer cursorTracer = ktx.pageCursorTracer();
        NodeCursor nodes = ktx.ambientNodeCursor();
        CursorFactory cursors = ktx.cursors();
        MutableLongSet nodeIds = new LongHashSet();
        MutableLongSet relIds = new LongHashSet();

        // Read-lock the node, and acquire a consistent view of its relationships and neighbours.
        locks.acquireShared( lockTracer, ResourceTypes.NODE, nodeId );
        ktx.dataRead().singleNode( nodeId, nodes );
        if ( nodes.next() )
        {
            nodeIds.add( nodes.nodeReference() );
            try ( var rels = RelationshipSelections.allCursor( cursors, nodes, null, cursorTracer ) )
            {
                while ( rels.next() )
                {
                    relIds.add( rels.relationshipReference() );
                    nodeIds.add( rels.sourceNodeReference() );
                    nodeIds.add( rels.targetNodeReference() );
                }
            }
        }

        // Lock all the nodes involved by following the node id ordering.
        locks.acquireExclusive( lockTracer, ResourceTypes.NODE, nodeIds.toSortedArray() );

        // Then finally remove all relationships incident on our node.
        int relationshipsDeleted = 0;
        for ( long relId : relIds.toSortedArray() )
        {
            if ( relationshipDeleter.test( relId ) )
            {
                relationshipsDeleted++;
            }
        }
        return relationshipsDeleted;
    }
}
