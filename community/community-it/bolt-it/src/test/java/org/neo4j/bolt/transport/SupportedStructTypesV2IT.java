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
package org.neo4j.bolt.transport;

import org.assertj.core.api.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import java.time.ZoneOffset;
import java.util.List;

import org.neo4j.bolt.packstream.Neo4jPackV2;
import org.neo4j.bolt.testing.TransportTestUtil;
import org.neo4j.bolt.testing.client.SecureSocketConnection;
import org.neo4j.bolt.testing.client.SecureWebSocketConnection;
import org.neo4j.bolt.testing.client.SocketConnection;
import org.neo4j.bolt.testing.client.TransportConnection;
import org.neo4j.bolt.testing.client.WebSocketConnection;
import org.neo4j.internal.helpers.HostnamePort;
import org.neo4j.values.AnyValue;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.runners.Parameterized.Parameters;
import static org.neo4j.bolt.testing.MessageConditions.msgRecord;
import static org.neo4j.bolt.testing.MessageConditions.msgSuccess;
import static org.neo4j.bolt.testing.StreamConditions.eqRecord;
import static org.neo4j.bolt.testing.TransportTestUtil.eventuallyReceives;
import static org.neo4j.bolt.transport.Neo4jWithSocket.withOptionalBoltEncryption;
import static org.neo4j.values.storable.CoordinateReferenceSystem.Cartesian;
import static org.neo4j.values.storable.CoordinateReferenceSystem.WGS84;
import static org.neo4j.values.storable.DateTimeValue.datetime;
import static org.neo4j.values.storable.DateValue.date;
import static org.neo4j.values.storable.DurationValue.duration;
import static org.neo4j.values.storable.LocalDateTimeValue.localDateTime;
import static org.neo4j.values.storable.LocalTimeValue.localTime;
import static org.neo4j.values.storable.TimeValue.time;
import static org.neo4j.values.storable.Values.longValue;
import static org.neo4j.values.storable.Values.pointValue;
import static org.neo4j.values.virtual.VirtualValues.map;

@RunWith( Parameterized.class )
public class SupportedStructTypesV2IT
{
    @Rule
    public Neo4jWithSocket server = new Neo4jWithSocket( getClass(), withOptionalBoltEncryption() );

    @Parameter
    public Class<? extends TransportConnection> connectionClass;

    private HostnamePort address;
    private TransportConnection connection;
    private TransportTestUtil util;

    @Parameters( name = "{0}" )
    public static List<Class<? extends TransportConnection>> transports()
    {
        return asList( SocketConnection.class, WebSocketConnection.class, SecureSocketConnection.class, SecureWebSocketConnection.class );
    }

    @Before
    public void setUp() throws Exception
    {
        address = server.lookupDefaultConnector();
        connection = connectionClass.getDeclaredConstructor().newInstance();
        util = new TransportTestUtil( new Neo4jPackV2() );
    }

    @After
    public void tearDown() throws Exception
    {
        if ( connection != null )
        {
            connection.disconnect();
        }
    }

    @Test
    public void shouldNegotiateProtocolV4() throws Exception
    {
        connection.connect( address )
                .send( util.defaultAcceptedVersions() )
                .send( util.defaultAuth() );

        assertThat( connection ).satisfies( util.eventuallyReceivesSelectedProtocolVersion() );
    }

    @Test
    public void shouldNegotiateProtocolV4WhenClientSupportsBothV4AndV3() throws Exception
    {
        connection.connect( address )
                .send( util.acceptedVersions( 4, 3, 0, 0 ) )
                .send( util.defaultAuth() );

        assertThat( connection ).satisfies( eventuallyReceives( new byte[]{0, 0, 0, 4} ) );
    }

    @Test
    public void shouldSendPoint2D() throws Exception
    {
        testSendingOfBoltV2Value( pointValue( WGS84, 39.111748, -76.775635 ) );
    }

    @Test
    public void shouldReceivePoint2D() throws Exception
    {
        testReceivingOfBoltV2Value( "RETURN point({x: 40.7624, y: 73.9738})", pointValue( Cartesian, 40.7624, 73.9738 ) );
    }

    @Test
    public void shouldSendAndReceivePoint2D() throws Exception
    {
        testSendingAndReceivingOfBoltV2Value( pointValue( WGS84, 38.8719, 77.0563 ) );
    }

    @Test
    public void shouldSendDuration() throws Exception
    {
        testSendingOfBoltV2Value( duration( 5, 3, 34, 0 ) );
    }

    @Test
    public void shouldReceiveDuration() throws Exception
    {
        testReceivingOfBoltV2Value( "RETURN duration({months: 3, days: 100, seconds: 999, nanoseconds: 42})", duration( 3, 100, 999, 42 ) );
    }

    @Test
    public void shouldSendAndReceiveDuration() throws Exception
    {
        testSendingAndReceivingOfBoltV2Value( duration( 17, 9, 2, 1_000_000 ) );
    }

    @Test
    public void shouldSendDate() throws Exception
    {
        testSendingOfBoltV2Value( date( 1991, 8, 24 ) );
    }

    @Test
    public void shouldReceiveDate() throws Exception
    {
        testReceivingOfBoltV2Value( "RETURN date('2015-02-18')", date( 2015, 2, 18 ) );
    }

    @Test
    public void shouldSendAndReceiveDate() throws Exception
    {
        testSendingAndReceivingOfBoltV2Value( date( 2005, 5, 22 ) );
    }

    @Test
    public void shouldSendLocalTime() throws Exception
    {
        testSendingOfBoltV2Value( localTime( 2, 35, 10, 1 ) );
    }

    @Test
    public void shouldReceiveLocalTime() throws Exception
    {
        testReceivingOfBoltV2Value( "RETURN localtime('11:04:35')", localTime( 11, 04, 35, 0 ) );
    }

    @Test
    public void shouldSendAndReceiveLocalTime() throws Exception
    {
        testSendingAndReceivingOfBoltV2Value( localTime( 22, 10, 10, 99 ) );
    }

    @Test
    public void shouldSendTime() throws Exception
    {
        testSendingOfBoltV2Value( time( 424242, ZoneOffset.of( "+08:30" ) ) );
    }

    @Test
    public void shouldReceiveTime() throws Exception
    {
        testReceivingOfBoltV2Value( "RETURN time('14:30+0100')", time( 14, 30, 0, 0, ZoneOffset.ofHours( 1 ) ) );
    }

    @Test
    public void shouldSendAndReceiveTime() throws Exception
    {
        testSendingAndReceivingOfBoltV2Value( time( 19, 22, 44, 100, ZoneOffset.ofHours( -5 ) ) );
    }

    @Test
    public void shouldSendLocalDateTime() throws Exception
    {
        testSendingOfBoltV2Value( localDateTime( 2002, 5, 22, 15, 15, 25, 0 ) );
    }

    @Test
    public void shouldReceiveLocalDateTime() throws Exception
    {
        testReceivingOfBoltV2Value( "RETURN localdatetime('20150202T19:32:24')", localDateTime( 2015, 2, 2, 19, 32, 24, 0 ) );
    }

    @Test
    public void shouldSendAndReceiveLocalDateTime() throws Exception
    {
        testSendingAndReceivingOfBoltV2Value( localDateTime( 1995, 12, 12, 10, 30, 0, 0 ) );
    }

    @Test
    public void shouldSendDateTimeWithTimeZoneName() throws Exception
    {
        testSendingOfBoltV2Value( datetime( 1956, 9, 14, 11, 20, 25, 0, "Europe/Stockholm" ) );
    }

    @Test
    public void shouldReceiveDateTimeWithTimeZoneName() throws Exception
    {
        testReceivingOfBoltV2Value( "RETURN datetime({year:1984, month:10, day:11, hour:21, minute:30, timezone:'Europe/London'})",
                datetime( 1984, 10, 11, 21, 30, 0, 0, "Europe/London" ) );
    }

    @Test
    public void shouldSendAndReceiveDateTimeWithTimeZoneName() throws Exception
    {
        testSendingAndReceivingOfBoltV2Value( datetime( 1984, 10, 11, 21, 30, 0, 0, "Europe/London" ) );
    }

    @Test
    public void shouldSendDateTimeWithTimeZoneOffset() throws Exception
    {
        testSendingOfBoltV2Value( datetime( 424242, 0, ZoneOffset.ofHoursMinutes( -7, -15 ) ) );
    }

    @Test
    public void shouldReceiveDateTimeWithTimeZoneOffset() throws Exception
    {
        testReceivingOfBoltV2Value( "RETURN datetime({year:2022, month:3, day:2, hour:19, minute:10, timezone:'+02:30'})",
                datetime( 2022, 3, 2, 19, 10, 0, 0, ZoneOffset.ofHoursMinutes( 2, 30 ) ) );
    }

    @Test
    public void shouldSendAndReceiveDateTimeWithTimeZoneOffset() throws Exception
    {
        testSendingAndReceivingOfBoltV2Value( datetime( 1899, 1, 1, 12, 12, 32, 0, ZoneOffset.ofHoursMinutes( -4, -15 ) ) );
    }

    private <T extends AnyValue> void testSendingOfBoltV2Value( T value ) throws Exception
    {
        handshakeAndAuth();

        connection.send( util.defaultRunAutoCommitTx( "CREATE (n:Node {value: $value}) RETURN 42", map( new String[]{"value"}, new AnyValue[]{value} ) ) );

        assertThat( connection ).satisfies( util.eventuallyReceives(
                msgSuccess(),
                msgRecord( eqRecord(new Condition<>( v -> v.equals( longValue( 42 ) ), "equals condition" ) ) ),
                msgSuccess() ) );
    }

    private <T extends AnyValue> void testReceivingOfBoltV2Value( String query, T expectedValue ) throws Exception
    {
        handshakeAndAuth();

        connection.send( util.defaultRunAutoCommitTx( query ) );

        assertThat( connection ).satisfies( util.eventuallyReceives(
                msgSuccess(),
                msgRecord( eqRecord( new Condition<>( v -> v.equals( expectedValue ), "equals condition" ) ) ),
                msgSuccess() ) );
    }

    private <T extends AnyValue> void testSendingAndReceivingOfBoltV2Value( T value ) throws Exception
    {
        handshakeAndAuth();

        connection.send( util.defaultRunAutoCommitTx( "RETURN $value", map( new String[]{"value"}, new AnyValue[]{value} ) ) );

        assertThat( connection ).satisfies( util.eventuallyReceives(
                msgSuccess(),
                msgRecord( eqRecord( new Condition<>( v -> v.equals( value ), "equals condition" ) ) ),
                msgSuccess() ) );
    }

    private void handshakeAndAuth() throws Exception
    {
        connection.connect( address )
                .send( util.defaultAcceptedVersions() )
                .send( util.defaultAuth() );

        assertThat( connection ).satisfies( util.eventuallyReceivesSelectedProtocolVersion() );
        assertThat( connection ).satisfies( util.eventuallyReceives( msgSuccess() ) );
    }
}
