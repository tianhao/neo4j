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
package org.neo4j.bolt.v41;

import java.time.Clock;

import org.neo4j.bolt.BoltChannel;
import org.neo4j.bolt.runtime.statemachine.BoltStateMachineSPI;
import org.neo4j.bolt.runtime.statemachine.impl.AbstractBoltStateMachine;
import org.neo4j.bolt.v3.runtime.ConnectedState;
import org.neo4j.bolt.v3.runtime.InterruptedState;
import org.neo4j.bolt.v4.runtime.AutoCommitState;
import org.neo4j.bolt.v4.runtime.FailedState;
import org.neo4j.bolt.v4.runtime.InTransactionState;
import org.neo4j.bolt.v4.runtime.ReadyState;

public class BoltStateMachineV41 extends AbstractBoltStateMachine
{
    public BoltStateMachineV41( BoltStateMachineSPI boltSPI, BoltChannel boltChannel, Clock clock )
    {
        super( boltSPI, boltChannel, clock );
    }

    @Override
    protected States buildStates()
    {
        ConnectedState connected = new ConnectedState();
        ReadyState ready = new ReadyState(); // v4
        AutoCommitState autoCommitState = new AutoCommitState(); // v4
        InTransactionState inTransaction = new InTransactionState(); // v4
        FailedState failed = new FailedState(); // v4
        InterruptedState interrupted = new InterruptedState();

        connected.setReadyState( ready );

        ready.setTransactionReadyState( inTransaction );
        ready.setStreamingState( autoCommitState );
        ready.setFailedState( failed );
        ready.setInterruptedState( interrupted );

        autoCommitState.setReadyState( ready );
        autoCommitState.setFailedState( failed );
        autoCommitState.setInterruptedState( interrupted );

        inTransaction.setReadyState( ready );
        inTransaction.setFailedState( failed );
        inTransaction.setInterruptedState( interrupted );

        failed.setInterruptedState( interrupted );

        interrupted.setReadyState( ready );

        return new States( connected, failed );
    }
}
