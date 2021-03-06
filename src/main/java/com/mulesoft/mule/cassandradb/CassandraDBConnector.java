/**
 * Mule Cassandra Connector
 *
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package com.mulesoft.mule.cassandradb;

import com.mulesoft.mule.cassandradb.api.IndexExpresion;
import org.apache.cassandra.thrift.*;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.mule.api.ConnectionException;
import org.mule.api.annotations.*;
import org.mule.api.annotations.display.Password;
import org.mule.api.annotations.display.Placement;
import org.mule.api.annotations.param.ConnectionKey;
import org.mule.api.annotations.param.Default;
import org.mule.api.annotations.param.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * The Apache Cassandra database is the right choice when you need scalability and high availability without compromising performance.
 * Cassandra's ColumnFamily data model offers the convenience of column indexes with the performance of log-structured updates, strong support for materialized views, and powerful built-in caching.
 *
 * @author MuleSoft, Inc.
 */
@Connector(name = "cassandradb", schemaVersion = "3.2", friendlyName = "CassandraDB", minMuleVersion = "3.5")
public class CassandraDBConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraDBConnector.class);

    /**
     * Host name or IP address
     */
    @Configurable
    @Default("localhost")
    private String host;

    /**
     * Port (default is 9160)
     */
    @Configurable
    @Default("9160")
    private int port = 9160;

    /**
     * Cassandra keyspace
     */
    @Configurable
    private String keyspace;

    /**
     * Consistency Level. Can be one of ANY, ONE (default), TWO, THREE, QUORUM,
     * LOCAL_QUORUM, EACH_QUORUM, ALL. See http://wiki.apache.org/cassandra/API
     * for more details.
     */
    @Configurable
    @Default("ONE")
    private ConsistencyLevel consistencyLevel;

    /**
     * Generic class that encapsulates the I/O layer. This is basically a thin
     * wrapper around the combined functionality of Java input/output streams.
     */
    private TTransport tr;
    /**
     * Cassandra Client
     */
    private Cassandra.Client client;

    /**
     * Method invoked when a connection is required
     *
     * @param username A username. NOTE: Please use a dummy username if you have disabled authentication
     * @param password A password. NOTE: Leave empty if not required. If specified, the connector will try to login with this credentials
     * @throws org.mule.api.ConnectionException
     */
    @Connect
    public void connect(@ConnectionKey String username,
                        @Password String password) throws ConnectionException {
        try {
            LOGGER.debug("Attempting to connect to Cassandra");
            tr = new TFramedTransport(new TSocket(host, port)); // NOSONAR
            client = CassandraDBUtils.getClient(host, port, keyspace, username, password, tr);
            tr.open();
            client.set_keyspace(this.getKeyspace());
            LOGGER.debug("Connection created: " + tr);

        } catch (Exception e) {
            LOGGER.error("Unable to connect to Casssandra DB instance", e);
            throw new org.mule.api.ConnectionException(
                    org.mule.api.ConnectionExceptionCode.UNKNOWN, null,
                    e.getMessage(), e);
        }
    }


    /**
     * Disconnect
     */
    @Disconnect
    public void disconnect() {
        if (isConnected()) {
            try {
                tr.flush();
                tr.close();
            } catch (Exception e) {
                LOGGER.error("Exception thrown while trying to disconnect:", e);
            }
        }
    }

    /**
     * Are we connected
     *
     * @return the connection status of the connector.
     */
    @ValidateConnection
    public boolean isConnected() {
        return tr != null && tr.isOpen();
    }

    /**
     * Connection Identifier
     *
     * @return the connection identifier.
     */
    @ConnectionIdentifier
    public String connectionId() {
        return "unknown";
    }

    /**
     * Set the keyspace to use for subsequent requests.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:set-query-keyspace}
     *
     * @param value New value that will be used to all following requests
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public void setQueryKeyspace(String value) throws CassandraDBException {
        try {
            setKeyspace(value);
            client.set_keyspace(value);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Get Column or SuperColumn by the path
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:get}
     *
     * @param rowKey            the row key
     * @param columnPath        Path to the column - must be in the form of
     *                          ColumnFamily:SuperColumn:Column.
     * @param columnSerializers Serializers for each column
     * @return the result as a JSON node
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public Object get(String rowKey, String columnPath, @Placement(group = "Columns Serializars")
    @Optional List<ColumnSerializer> columnSerializers) throws CassandraDBException {

        LOGGER.debug("Retrieving the data from column path: " + columnPath);
        ColumnPath cPath = CassandraDBUtils.parseColumnPath(columnPath);
        ColumnOrSuperColumn result;
        try {
            result = client.get(
                    CassandraDBUtils.toByteBuffer(rowKey), cPath,
                    this.getConsistencyLevel());
            LOGGER.debug("ColumnPath : " + cPath + " ; result is : " + result);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (NotFoundException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }

        return CassandraDBUtils.columnOrSuperColumnToMap(result,
                columnSerializers);
    }

    /**
     * Get Column or SuperColumn by the path
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:get}
     *
     * @param rowKey            the row key
     * @param columnPath        Path to the column
     * @param columnSerializers Serializers for each column
     * @return the result as a JSON node
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public Object getRow(String rowKey, ColumnPath columnPath, @Placement(group = "Columns Serializars")
    @Optional List<ColumnSerializer> columnSerializers) throws CassandraDBException {
        LOGGER.debug("Retrieving the data from column path: " + columnPath);

        ColumnOrSuperColumn result;
        try {
            result = client.get(
                    CassandraDBUtils.toByteBuffer(rowKey), columnPath,
                    this.getConsistencyLevel());
            LOGGER.debug("ColumnPath : " + columnPath + " ; result is : " + result);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (NotFoundException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }

        return CassandraDBUtils.columnOrSuperColumnToMap(result,
                columnSerializers);
    }

    /**
     * Get the group of columns contained by column_parent (either a
     * ColumnFamily name or a ColumnFamily/SuperColumn name pair) specified by
     * the given SlicePredicate (start, finish, reversed and count) parameters.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:get-slice}
     *
     * @param rowKey            the row key
     * @param columnParent      Path to the column - must be a name of the ColumnFamily or
     *                          ColumnFamily:SuperColumn pair
     * @param start             The column name to start the slice with. This attribute is not required, though there is no default value,
     *                          and can be safely set to '', i.e., an empty byte array, to start with the first column name. Otherwise, it
     *                          must a valid value under the rules of the Comparator defined for the given ColumnFamily.
     * @param finish            The column name to stop the slice at. This attribute is not required, though there is no default value,
     *                          and can be safely set to an empty byte array to not stop until 'count' results are seen. Otherwise, it
     *                          must also be a valid value to the ColumnFamily Comparator.
     * @param reversed          Whether the results should be ordered in reversed order. Similar to ORDER BY blah DESC in SQL.
     * @param count             How many columns to return. Similar to LIMIT in SQL. May be arbitrarily large, but Thrift will
     *                          materialize the whole result into memory before returning it to the client, so be aware that you may
     *                          be better served by iterating through slices by passing the last value of one call in as the 'start'
     *                          of the next instead of increasing 'count' arbitrarily large.
     * @param columnSerializers Serializers for each column
     * @return the result as a JSON node
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor(name = "get-slice")
    public Object getSlice(String rowKey, String columnParent,
                           @Optional String start, @Optional String finish,
                           @Default("false") boolean reversed,
                           @Default("100") int count,
                           @Placement(group = "Columns Serializars") @Optional List<ColumnSerializer> columnSerializers) throws CassandraDBException {
        LOGGER.debug("Get Slice: ROW KEY= " + rowKey + " COLUMN PARENT="
                + columnParent + " START=" + start + " FINISH=" + finish
                + " REVERSED=" + reversed + " COUNT=" + count);

        ColumnParent cParent = CassandraDBUtils
                .generateColumnParent(columnParent);

        SlicePredicate predicate = new SlicePredicate();
        SliceRange range = CassandraDBUtils.generateSliceRange(start, finish,
                reversed, count);

        predicate.setSlice_range(range);
        List<ColumnOrSuperColumn> columnsByKey;
        try {
            columnsByKey = client.get_slice(
                    CassandraDBUtils.toByteBuffer(rowKey), cParent, predicate,
                    this.getConsistencyLevel());
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }

        return CassandraDBUtils.listOfColumnsToMap(columnsByKey,
                columnSerializers);
    }

    /**
     * Retrieves slices for column_parent and predicate on each of the given
     * keys in parallel. Keys are a list<string> of the keys to get slices for.
     * This is similar to getRangeSlices, except it operates on a set of
     * non-contiguous keys instead of a range of keys.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:multiget-slice}
     *
     * @param rowKeys           A list of keys used for
     * @param columnParent      Path to the column - must be a name of the ColumnFamily or
     *                          ColumnFamily:SuperColumn pair
     * @param start             The column name to start the slice with. This attribute is not required, though there is no default value,
     *                          and can be safely set to '', i.e., an empty byte array, to start with the first column name. Otherwise, it
     *                          must a valid value under the rules of the Comparator defined for the given ColumnFamily.
     * @param finish            The column name to stop the slice at. This attribute is not required, though there is no default value,
     *                          and can be safely set to an empty byte array to not stop until 'count' results are seen. Otherwise, it
     *                          must also be a valid value to the ColumnFamily Comparator.
     * @param reversed          Whether the results should be ordered in reversed order. Similar to ORDER BY blah DESC in SQL.
     * @param count             How many columns to return. Similar to LIMIT in SQL. May be arbitrarily large, but Thrift will
     *                          materialize the whole result into memory before returning it to the client, so be aware that you may
     *                          be better served by iterating through slices by passing the last value of one call in as the 'start'
     *                          of the next instead of increasing 'count' arbitrarily large.
     * @param columnSerializers Serializers for each column
     * @return A map of keys and ColumnOrSuperColumn
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor(name = "multiget-slice", friendlyName = "Multiget slice")
    public Object multiGetSlice(
            @Placement(group = "row-keys") List<String> rowKeys,
            String columnParent,
            @Optional String start,
            @Optional String finish,
            @Default("false") boolean reversed,
            @Default("100") int count,
            @Optional @Placement(group = "Column Serializers") List<ColumnSerializer> columnSerializers)
            throws CassandraDBException {

        List<ByteBuffer> keys = CassandraDBUtils.toByteBufferList(rowKeys);

        ColumnParent cParent = CassandraDBUtils
                .generateColumnParent(columnParent);

        SlicePredicate predicate = new SlicePredicate();
        SliceRange range = CassandraDBUtils.generateSliceRange(start, finish,
                reversed, count);

        predicate.setSlice_range(range);

        // For now we just return the map...leaving this variable in case we
        // want to format the data to a new Type
        Map<ByteBuffer, List<ColumnOrSuperColumn>> result;
        try {
            result = client
                    .multiget_slice(keys, cParent, predicate,
                            this.getConsistencyLevel());
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }

        return result;
    }

    /**
     * Counts the columns present in column_parent within the predicate.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:get-count}
     *
     * @param rowKey       the row key
     * @param columnParent Path to the column - must be a name of the ColumnFamily or
     *                     ColumnFamily:SuperColumn pair
     * @param start        The column name to start the slice with. This attribute is not required, though there is no default value,
     *                     and can be safely set to '', i.e., an empty byte array, to start with the first column name. Otherwise, it
     *                     must a valid value under the rules of the Comparator defined for the given ColumnFamily.
     * @param finish       The column name to stop the slice at. This attribute is not required, though there is no default value,
     *                     and can be safely set to an empty byte array to not stop until 'count' results are seen. Otherwise, it
     *                     must also be a valid value to the ColumnFamily Comparator.
     * @param reversed     Whether the results should be ordered in reversed order. Similar to ORDER BY blah DESC in SQL.
     * @param count        How many columns to return. Similar to LIMIT in SQL. May be arbitrarily large, but Thrift will
     *                     materialize the whole result into memory before returning it to the client, so be aware that you may
     *                     be better served by iterating through slices by passing the last value of one call in as the 'start'
     *                     of the next instead of increasing 'count' arbitrarily large.
     * @return Count of register that met the conditions
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor(name = "get-count")
    public int getCount(String rowKey, String columnParent,
                        @Optional String start, @Optional String finish,
                        @Default("false") boolean reversed,
                        @Default("100") int count) throws CassandraDBException {

        ColumnParent cParent = CassandraDBUtils
                .generateColumnParent(columnParent);

        SlicePredicate predicate = new SlicePredicate();
        SliceRange range = CassandraDBUtils.generateSliceRange(start, finish,
                reversed, count);

        predicate.setSlice_range(range);
        try {
            return client.get_count(CassandraDBUtils.toByteBuffer(rowKey), cParent,
                    predicate, this.getConsistencyLevel());
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * A combination of multiget_slice and get_count.
     * <p/>
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:multiget-count}
     *
     * @param rowKeys      A list of keys used for
     * @param columnParent Path to the column - must be a name of the ColumnFamily or
     *                     ColumnFamily:SuperColumn pair
     * @param start        The column name to start the slice with. This attribute is not required, though there is no default value,
     *                     and can be safely set to '', i.e., an empty byte array, to start with the first column name. Otherwise, it
     *                     must a valid value under the rules of the Comparator defined for the given ColumnFamily.
     * @param finish       The column name to stop the slice at. This attribute is not required, though there is no default value,
     *                     and can be safely set to an empty byte array to not stop until 'count' results are seen. Otherwise, it
     *                     must also be a valid value to the ColumnFamily Comparator.
     * @param reversed     Whether the results should be ordered in reversed order. Similar to ORDER BY blah DESC in SQL.
     * @param count        How many columns to return. Similar to LIMIT in SQL. May be arbitrarily large, but Thrift will
     *                     materialize the whole result into memory before returning it to the client, so be aware that you may
     *                     be better served by iterating through slices by passing the last value of one call in as the 'start'
     *                     of the next instead of increasing 'count' arbitrarily large.
     * @return A map of keys and integers
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor(name = "multiget-count", friendlyName = "Multiget count")
    public Object multiGetCount(@Placement(group = "row-keys") List<String> rowKeys,
                                String columnParent, @Optional String start,
                                @Optional String finish, @Default("false") boolean reversed,
                                @Default("100") int count) throws CassandraDBException {

        List<ByteBuffer> keys = CassandraDBUtils.toByteBufferList(rowKeys);

        ColumnParent cParent = CassandraDBUtils
                .generateColumnParent(columnParent);

        SlicePredicate predicate = new SlicePredicate();
        SliceRange range = CassandraDBUtils.generateSliceRange(start, finish,
                reversed, count);

        predicate.setSlice_range(range);

        try {
            return client.multiget_count(keys, cParent,
                    predicate, this.getConsistencyLevel());
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Replaces get_range_slice. Returns a list of slices for the keys within
     * the specified KeyRange. Unlike get_key_range, this applies the given
     * predicate to all keys in the range, not just those with undeleted
     * matching data. Note that when using RandomPartitioner, keys are stored in
     * the order of their MD5 hash, making it impossible to get a meaningful
     * range of keys between two endpoints.
     * <p/>
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:get-range-slices}
     *
     * @param columnParent  Path to the column - must be a name of the ColumnFamily or
     *                      ColumnFamily:SuperColumn pair
     * @param start         The column name to start the slice with. This attribute is not required, though there is no default value,
     *                      and can be safely set to '', i.e., an empty byte array, to start with the first column name. Otherwise, it
     *                      must a valid value under the rules of the Comparator defined for the given ColumnFamily.
     * @param finish        The column name to stop the slice at. This attribute is not required, though there is no default value,
     *                      and can be safely set to an empty byte array to not stop until 'count' results are seen. Otherwise, it
     *                      must also be a valid value to the ColumnFamily Comparator.
     * @param reversed      Whether the results should be ordered in reversed order. Similar to ORDER BY blah DESC in SQL.
     * @param count         How many columns to return. Similar to LIMIT in SQL. May be arbitrarily large, but Thrift will
     *                      materialize the whole result into memory before returning it to the client, so be aware that you may
     *                      be better served by iterating through slices by passing the last value of one call in as the 'start'
     *                      of the next instead of increasing 'count' arbitrarily large.
     * @param startKey      The first key in the inclusive KeyRange.
     * @param endKey        The last key in the inclusive KeyRange.
     * @param startToken    The first token in the exclusive KeyRange.
     * @param endToken      The last token in the exclusive KeyRange.
     * @param keyRangeCount The total number of keys to permit in the KeyRange.
     * @return List of objects KeySlice
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public Object getRangeSlices(String columnParent, @Optional String start,
                                 @Optional String finish, @Default("false") boolean reversed,
                                 @Default("100") int count, @Optional String startKey,
                                 @Optional String endKey, @Optional String startToken,
                                 @Optional String endToken, @Default("100") int keyRangeCount)
            throws CassandraDBException {

        ColumnParent cParent = CassandraDBUtils
                .generateColumnParent(columnParent);

        SlicePredicate predicate = new SlicePredicate();
        SliceRange range = CassandraDBUtils.generateSliceRange(start, finish,
                reversed, count);

        predicate.setSlice_range(range);

        KeyRange keyRange = new KeyRange();
        keyRange.setCount(keyRangeCount)
                .setStart_key(CassandraDBUtils.toByteBuffer(startKey))
                .setEnd_key(CassandraDBUtils.toByteBuffer(endKey))
                .setStart_token(startToken).setEnd_token(endToken);

        try {
            return client.get_range_slices(cParent, predicate, keyRange,
                    this.getConsistencyLevel());
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Like get_range_slices, returns a list of slices, but uses IndexClause
     * instead of KeyRange. To use this method, the underlying ColumnFamily of
     * the ColumnParent must have been configured with a column_metadata
     * attribute, specifying at least the name and index_type attributes. See
     * CfDef and ColumnDef above for the list of attributes. Note: the
     * IndexClause must contain one IndexExpression with an EQ operator on a
     * configured index column. Other IndexExpression structs may be added to
     * the IndexClause for non-indexed columns to further refine the results of
     * the EQ expression.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:get-indexed-slices}
     *
     * @param columnParent   Path to the column - must be a name of the ColumnFamily or
     *                       ColumnFamily:SuperColumn pair
     * @param start          The column name to start the slice with. This attribute is not required, though there is no default value,
     *                       and can be safely set to '', i.e., an empty byte array, to start with the first column name. Otherwise, it
     *                       must a valid value under the rules of the Comparator defined for the given ColumnFamily.
     * @param finish         The column name to stop the slice at. This attribute is not required, though there is no default value,
     *                       and can be safely set to an empty byte array to not stop until 'count' results are seen. Otherwise, it
     *                       must also be a valid value to the ColumnFamily Comparator.
     * @param reversed       Whether the results should be ordered in reversed order. Similar to ORDER BY blah DESC in SQL.
     * @param count          How many columns to return. Similar to LIMIT in SQL. May be arbitrarily large, but Thrift will
     *                       materialize the whole result into memory before returning it to the client, so be aware that you may
     *                       be better served by iterating through slices by passing the last value of one call in as the 'start'
     *                       of the next instead of increasing 'count' arbitrarily large.
     * @param clauseCount    The number of results to which the index query will be
     *                       constrained
     * @param clauseStartKey Start the index query at the specified key - can be set to '',
     *                       i.e., an empty byte array, to start with the first key
     * @param expressionList The list of IndexExpression objects which must contain one EQ
     *                       IndexOperator among the expressions
     * @return List of objects KeySlice
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public Object getIndexedSlices(String columnParent, @Optional String start,
                                   @Optional String finish,
                                   @Default("false") boolean reversed,
                                   @Default("100") int count,
                                   @Default("100") int clauseCount, String clauseStartKey,
                                   List<IndexExpresion> expressionList)
            throws CassandraDBException {

        ColumnParent cParent = CassandraDBUtils
                .generateColumnParent(columnParent);

        SlicePredicate predicate = new SlicePredicate();
        SliceRange range = CassandraDBUtils.generateSliceRange(start, finish,
                reversed, count);

        predicate.setSlice_range(range);

        IndexClause indexClause = new IndexClause();
        indexClause.setCount(clauseCount);
        indexClause.setStart_key(CassandraDBUtils.toByteBuffer(clauseStartKey));
        List<IndexExpression> expList = CassandraDBUtils.toIndexExpression(expressionList);
        indexClause.setExpressions(expList);

        try {
            return client.get_indexed_slices(cParent, indexClause, predicate,
                    this.getConsistencyLevel());
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Insert a Column consisting of (name, value, timestamp, ttl) at the given
     * ColumnParent. Note that a SuperColumn cannot directly contain binary
     * values -- it can only contain sub-Columns. Only one sub-Column may be
     * inserted at a time, as well.
     * Any number of columns may be inserted at the same time. When inserting or updating columns in a column family, the client application specifies the row key to identify which column records to update.
     * The row key is similar to a primary key in that it must be unique for each row within a column family.
     * However, unlike a primary key, inserting a duplicate row key will not result in a primary key constraint violation - it will be treated as an UPSERT (update the specified columns in that row if they exist or insert them if they do not).
     * For more details check: http://www.datastax.com/docs/0.8/dml/about_writes
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:insert}
     *
     * @param rowKey       The row key
     * @param columnParent The ColumnParent
     * @param columnName   The name of the column
     * @param columnValue  The value of the column
     * @param ttl          An optional, positive delay (in seconds) after which the
     *                     Column will be automatically deleted.
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public void insert(String rowKey, String columnParent, String columnName,
                       String columnValue, @Default("0") int ttl)
            throws CassandraDBException {
        ColumnParent cParent = CassandraDBUtils
                .generateColumnParent(columnParent);
        Column column = new Column(CassandraDBUtils.toByteBuffer(columnName));
        column.setValue(CassandraDBUtils.toByteBuffer(columnValue));
        column.setTimestamp(System.currentTimeMillis());
        if (ttl > 0) {
            column.setTtl(ttl);
        }
        try {
            client.insert(CassandraDBUtils.toByteBuffer(rowKey), cParent, column,
                    this.getConsistencyLevel());
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Insert object into the database
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample cassandradb:insert-from-map}
     *
     * @param content Content to be inserted into the database. Must be an instance of Map in the following format:
     *                <p/>
     *                {
     *                "ToyStores" : {                           - Column Family
     *                "Ohio Store" : {                        - RowKey
     *                "Transformer" : {                     - SuperColumn
     *                "Price" : "29.99",                  - Column
     *                "Section" : "Action Figures"
     *                }
     *                "GumDrop" : {
     *                "Price" : "0.25",
     *                "Section" : "Candy"
     *                }
     *                "MatchboxCar" : {
     *                "Price" : "1.49",
     *                "Section" : "Vehicles"
     *                }
     *                }
     *                "New York Store" : {
     *                "JawBreaker" : {
     *                "Price" : "4.25",
     *                "Section" : "Candy"
     *                }
     *                "MatchboxCar" : {
     *                "Price" : "8.79",
     *                "Section" : "Vehicles"
     *                }
     *                }
     *                }
     *                }
     * @return Same content
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public Map insertFromMap(@Default("#[payload]") Map content) throws CassandraDBException {
        LOGGER.debug("Inserting the data: " + content);

        //Iterate through ColumnFamilies
        for (Object key : content.keySet()) {
            String nextCFName = (String) key;

            Map<ByteBuffer, Map<String, List<Mutation>>> mutationsMap = new HashMap<ByteBuffer, Map<String, List<Mutation>>>();

            try {
                CfDef cfDef = new CfDef(keyspace, nextCFName);
                cfDef.column_type = "Super";
                client.system_add_column_family(cfDef);
            } catch (Exception e) {
                //Assume CF already exists:
                LOGGER.warn("ColumnFamily '" + nextCFName + "' already exists; message: " + e);
            }

            //Get SuperColumns of this CF
            Map superColumnsMap = (Map) content.get(nextCFName);
            //Iterate over RowKeys
            for (Object rowKey : superColumnsMap.keySet()) {

                Map<String, List<Mutation>> insertDataMap = new HashMap<String, List<Mutation>>();
                List<Mutation> rowData = new ArrayList<Mutation>();

                String nextRowKey = (String) rowKey;
                Map nextSCMap = (Map) superColumnsMap.get(nextRowKey);
                //Iterate over super column names
                for (Object superColumnName : nextSCMap.keySet()) {
                    String nextSCName = (String) superColumnName;
                    List<Column> columnsList = new ArrayList<Column>();
                    //Get Map of Columns
                    Map columnsMap = (Map) nextSCMap.get(nextSCName);
                    for (Object columnName : columnsMap.keySet()) {
                        String nextColumnName = (String) columnName;
                        Object nextColumnValue = columnsMap.get(nextColumnName);

                        Column nextColumn = new Column(CassandraDBUtils.toByteBuffer(nextColumnName));
                        nextColumn.setValue(CassandraDBUtils.toByteBuffer(nextColumnValue));
                        nextColumn.setTimestamp(System.currentTimeMillis());
                        columnsList.add(nextColumn);
                    }

                    SuperColumn nextSuperColumn = new SuperColumn(CassandraDBUtils.toByteBuffer(nextSCName),
                            columnsList);
                    ColumnOrSuperColumn columnOrSuperColumn = new ColumnOrSuperColumn();
                    columnOrSuperColumn.setSuper_column(nextSuperColumn);
                    Mutation m = new Mutation();
                    m.setColumn_or_supercolumn(columnOrSuperColumn);

                    rowData.add(m);
                }

                insertDataMap.put(nextCFName, rowData);
                mutationsMap.put(CassandraDBUtils.toByteBuffer(nextRowKey), insertDataMap);
            }

            try {
                client.batch_mutate(mutationsMap, this.getConsistencyLevel());
            } catch (InvalidRequestException e) {
                throw new CassandraDBException(e.getMessage(), e);
            } catch (UnavailableException e) {
                throw new CassandraDBException(e.getMessage(), e);
            } catch (TimedOutException e) {
                throw new CassandraDBException(e.getMessage(), e);
            } catch (TException e) {
                throw new CassandraDBException(e.getMessage(), e);
            }
        }

        return content;
    }

    /**
     * Executes the specified mutations on the keyspace. content is a
     * Map&lt;string, Map&lt;string, List&lt;Mutation&gt;&gt;&gt;; the outer map maps the key to
     * the inner map, which maps the column family to the Mutation; can be read
     * as: <code> Map&lt;key : string, Map&lt;column_family : string, List&lt;Mutation&gt;&gt;&gt;</code>. To
     * be more specific, the outer map key is a row key, the inner map key is
     * the column family name. A Mutation specifies either columns to insert or
     * columns to delete. See Mutation and Deletion above for more details.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:batch-mutable}
     *
     * @param content A Map&lt;ByteBuffer, Map&lt;String, List&lt;Mutation&gt;&gt;&gt;
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public void batchMutable(@Default("#[payload]") Map content) throws CassandraDBException {
        LOGGER.debug("Batch mutable called with: " + content);

        try {
            client.batch_mutate(content, this.getConsistencyLevel());
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Increments a CounterColumn consisting of (name, value) at the given
     * ColumnParent. Note that a SuperColumn cannot directly contain binary
     * values -- it can only contain sub-Columns.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:add}
     *
     * @param rowKey       Key of the row
     * @param columnParent Column Parent of the CounterColum
     * @param counterName  Name of the column
     * @param counterValue Value of the counter
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public void add(String rowKey, String columnParent, String counterName,
                    int counterValue) throws CassandraDBException {

        ColumnParent cParent = CassandraDBUtils
                .generateColumnParent(columnParent);

        CounterColumn column = new CounterColumn();
        column.setName(CassandraDBUtils.toByteBuffer(counterName));
        column.setValue(counterValue);

        try {
            client.add(CassandraDBUtils.toByteBuffer(rowKey), cParent, column,
                    this.getConsistencyLevel());
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Remove data from the row specified by key at the granularity specified by
     * column_path, and the given timestamp. Note that all the values in
     * column_path besides column_path.column_family are truly optional: you can
     * remove the entire row by just specifying the ColumnFamily, or you can
     * remove a SuperColumn or a single Column by specifying those levels too.
     * Note that the timestamp is needed, so that if the commands are replayed
     * in a different order on different nodes, the same result is produced.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:remove}
     *
     * @param rowKey     the row key
     * @param columnPath Path to the column - must be in the form of
     *                   ColumnFamily:SuperColumn:Column
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public void remove(String rowKey, String columnPath) throws CassandraDBException {
        ColumnPath cPath = CassandraDBUtils.parseColumnPath(columnPath);
        try {
            client.remove(CassandraDBUtils.toByteBuffer(rowKey), cPath,
                    new Date().getTime(), this.getConsistencyLevel());
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Remove a counter from the row specified by key at the granularity
     * specified by column_path. Note that all the values in column_path besides
     * column_path.column_family are truly optional: you can remove the entire
     * row by just specifying the ColumnFamily, or you can remove a SuperColumn
     * or a single Column by specifying those levels too. Note that counters
     * have limited support for deletes: if you remove a counter, you must wait
     * to issue any following update until the delete has reached all the nodes
     * and all of them have been fully compacted.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:remove-counter}
     *
     * @param rowKey     Key of the row
     * @param columnPath Column that will be affected by the remove counter
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public void removeCounter(String rowKey, String columnPath)
            throws CassandraDBException {
        ColumnPath cPath = CassandraDBUtils.parseColumnPath(columnPath);
        try {
            client.remove_counter(CassandraDBUtils.toByteBuffer(rowKey), cPath,
                    this.getConsistencyLevel());
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Removes all the rows from the given column family.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:truncate}
     *
     * @param columnFamily Column Family
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public void truncate(String columnFamily) throws CassandraDBException {
        try {
            client.truncate(columnFamily);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Gets the name of the cluster.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:describe-cluster-name}
     *
     * @return the cluster name
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public String describeClusterName() throws CassandraDBException {
        try {
            return client.describe_cluster_name();
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * For each schema version present in the cluster, returns a list of nodes
     * at that version. Hosts that do not respond will be under the key
     * DatabaseDescriptor.INITIAL_VERSION. The cluster is all on the same
     * version if the size of the map is 1.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:describe-schema-versions}
     *
     * @return A map of type Map<String,List<String>>
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public Map describeSchemaVersions() throws CassandraDBException {
        try {
            return client.describe_schema_versions();
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Gets information about the specified keyspace.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:describe-keyspace}
     *
     * @param keyspace Name of the keyspace
     * @return A KsDef instance
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public Object describeKeyspace(String keyspace) throws CassandraDBException {
        try {
            return client.describe_keyspace(keyspace);
        } catch (NotFoundException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Gets a list of all the keyspaces configured for the cluster. (Equivalent
     * to calling describe_keyspace(k) for k in keyspaces.)
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:describe-keyspaces}
     *
     * @return A list of KsDef
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public List describeKeyspaces() throws CassandraDBException {
        try {
            return client.describe_keyspaces();
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Gets the name of the partitioner for the cluster.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:describe-partitioner}
     *
     * @return Partitioner name
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public String describePartitioner() throws CassandraDBException {
        try {
            return client.describe_partitioner();
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Gets the token ring; a map of ranges to host addresses. Represented as a
     * set of TokenRange instead of a map from range to list of endpoints,
     * because you can't use Thrift structs as map keys:
     * https://issues.apache.org/jira/browse/THRIFT-162 for the same reason, we
     * can't return a set here, even though order is neither important nor
     * predictable.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample cassandradb:describe-ring}
     *
     * @param keyspace Keyspace name
     * @return A list of TokenRange
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public List describeRing(String keyspace) throws CassandraDBException {
        try {
            return client.describe_ring(keyspace);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Gets the name of the snitch used for the cluster.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:describe-snitch}
     *
     * @return String with the name of the snitch
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public String describeSnitch() throws CassandraDBException {
        try {
            return client.describe_snitch();
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Gets the Thrift API version.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:describe-version}
     *
     * @return API Version string representation
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public String describeVersion() throws CassandraDBException {
        try {
            return client.describe_version();
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Adds a column family. This method will throw an exception if a column
     * family with the same name is already associated with the keyspace.
     * Column names in metadata need to be ByteBuffer. You can achieve this by using the CassandraDBUtils like in the next example
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:system-add-column-family-from-object}
     *
     * @param cfDefinition An instance of CfDef
     * @return String The new Schema version ID.
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public String systemAddColumnFamilyFromObject(CfDef cfDefinition) throws CassandraDBException {
        try {
            return client.system_add_column_family(cfDefinition);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (SchemaDisagreementException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Adds a column family. This method will throw an exception if a column
     * family with the same name is already associated with the keyspace. This method also recieves a list of string to be used as column names
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:system-add-column-family-from-object-with-simple-names}
     *
     * @param cfDefinition An instance of CfDef
     * @param columnNames  A list that contains the columns names for columns defined in column_metadata
     * @return The new Schema version ID.
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public String systemAddColumnFamilyFromObjectWithSimpleNames(CfDef cfDefinition, List<String> columnNames)
            throws CassandraDBException {

        if (columnNames.size() != cfDefinition.column_metadata.size()) {
            throw new CassandraDBException("Provided column names and column metadata sizes are not the same.");
        }

        Iterator<String> nameIterator = columnNames.iterator();
        for (ColumnDef col : cfDefinition.column_metadata) {
            col.setName(CassandraDBUtils.toByteBuffer(nameIterator.next()));
        }

        try {
            return client.system_add_column_family(cfDefinition);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (SchemaDisagreementException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Adds a column family to the current keyspace. This method will throw an exception if a column
     * family with the same name is already associated with the keyspace.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:system-add-column-family-with-params}
     *
     * @param columnFamilyName       The name of the column to be added
     * @param comparatorType         Validator to use to validate and compare column names in
     *                               this column family. For Standard column families it applies to columns, for
     *                               Super column families applied to  super columns.Default is BytesType, which is a straight forward lexical
     *                               comparison of the bytes in each column.
     *                               <p/>
     *                               <p>Supported values are:</p>
     *                               <ul>
     *                               <li>AsciiType</li>
     *                               <li>BooleanType</li>
     *                               <li>BytesType</li>
     *                               <li>CounterColumnType (distributed counter column)</li>
     *                               <li>DateType</li>
     *                               <li>DoubleType</li>
     *                               <li>FloatType</li>
     *                               <li>Int32Type</li>
     *                               <li>IntegerType (a generic variable-length integer type)</li>
     *                               <li>LexicalUUIDType</li>
     *                               <li>LongType</li>
     *                               <li>UTF8Type</li>
     *                               <li>CompositeType (should be used with sub-types specified e.g. 'CompositeType(UTF8Type, Int32Type)'
     *                               quotes are important (!) in this case)</li>
     *                               </ul>
     *                               It is also valid to specify the fully-qualified class name to a class that
     *                               extends org.apache.cassandra.db.marshal.AbstractType.
     * @param keyValidationClass     Validator to use for keys. Default is BytesType which applies no validation.
     *                               <p/>
     *                               <p>Supported values are:</p>
     *                               <ul>
     *                               <li>AsciiType</li>
     *                               <li>BooleanType</li>
     *                               <li>BytesType</li>
     *                               <li>DateType</li>
     *                               <li>DoubleType</li>
     *                               <li>FloatType</li>
     *                               <li>Int32Type</li>
     *                               <li>IntegerType (a generic variable-length integer type)</li>
     *                               <li>LexicalUUIDType</li>
     *                               <li>LongType</li>
     *                               <li>UTF8Type</li>
     *                               </ul>
     *                               It is also valid to specify the fully-qualified class name to a class that
     *                               extends org.apache.cassandra.db.marshal.AbstractType.
     * @param defaultValidationClass Validator to use for values in columns which are
     *                               not listed in the column_metadata. Default is BytesType which applies
     *                               no validation.
     *                               <p/>
     *                               <p>Supported values are:</p>
     *                               <ul>
     *                               <li>AsciiType</li>
     *                               <li>BooleanType</li>
     *                               <li>BytesType</li>
     *                               <li>CounterColumnType (distributed counter column)</li>
     *                               <li>DateType</li>
     *                               <li>DoubleType</li>
     *                               <li>FloatType</li>
     *                               <li>Int32Type</li>
     *                               <li>IntegerType (a generic variable-length integer type)</li>
     *                               <li>LexicalUUIDType</li>
     *                               <li>LongType</li>
     *                               <li>UTF8Type</li>
     *                               <li>CompositeType (should be used with sub-types specified e.g. 'CompositeType(UTF8Type, Int32Type)'
     *                               quotes are important (!) in this case)</li>
     *                               </ul>
     *                               It is also valid to specify the fully-qualified class name to a class that
     *                               extends org.apache.cassandra.db.marshal.AbstractType.
     * @param columnMetadata         A list with the column definitions. If null, a dynamic column family will be created. For more details check http://www.datastax.com/docs/1.0/ddl/column_family
     * @param keyspace               The name of the keyspace. If null, current keyspace will be used
     * @return The new Schema version ID.
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public String systemAddColumnFamilyWithParams(String columnFamilyName, @Default("BytesType") String comparatorType,
                                                  @Default("BytesType") String keyValidationClass,
                                                  @Default("BytesType") String defaultValidationClass,
                                                  @Optional List<ColumnDef> columnMetadata,
                                                  @Optional String keyspace) throws CassandraDBException {

        this.setQueryKeyspace(keyspace == null ? this.getKeyspace() : keyspace);
        CfDef columnDefinition = new CfDef(getKeyspace(), columnFamilyName);

        columnDefinition.setComparator_type(comparatorType);
        columnDefinition.setKey_validation_class(keyValidationClass);
        columnDefinition.setDefault_validation_class(defaultValidationClass);
        columnDefinition.setColumn_metadata(columnMetadata);

        return this.systemAddColumnFamilyFromObject(columnDefinition);
    }

    /**
     * Drops a column family. Creates a snapshot and then submits a 'graveyard'
     * compaction during which the abandoned files will be deleted.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:system-drop-column-family}
     *
     * @param columnFamily The name of the column family to be drop
     * @return The new schema version ID.
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public String systemDropColumnFamily(String columnFamily)
            throws CassandraDBException {
        try {
            return client.system_drop_column_family(columnFamily);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (SchemaDisagreementException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Creates a new keyspace and any column families defined with it. Callers
     * are not required to first create an empty keyspace and then create column
     * families for it.
     * <p/>
     * This will create a keyspace named MyKeyspace with no column families
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:system-add-keyspace-from-object}
     *
     * @param keyspaceDefinition The keyspace definition that will be added
     * @return The new schema version ID.
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public String systemAddKeyspaceFromObject(KsDef keyspaceDefinition)
            throws CassandraDBException {
        try {
            return client.system_add_keyspace(keyspaceDefinition);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (SchemaDisagreementException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Creates a new keyspace with the provided name with all the defaults values
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:system-add-keyspace-with-params}
     *
     * @param keyspaceName    The keyspace name
     * @param columnNames     List of the column names the keyspace will have
     * @param strategyClass   The name of the class that handles the Replication Strategy.
     * @param strategyOptions Map containing the configuration for the strategy class selected.
     * @return The new schema version ID.
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public Object systemAddKeyspaceWithParams(String keyspaceName,
                                              @Placement(group = "Column Names") @Optional List<String> columnNames,
                                              @Optional ReplicationStrategy strategyClass,
                                              @Placement(group = "Strategy Options") @Optional Map<String, String> strategyOptions)
            throws CassandraDBException {
        KsDef ksDef = new KsDef();
        ksDef.setName(keyspaceName);
        if (strategyClass == null) {
            ksDef.setStrategy_class(ReplicationStrategy.SIMPLE.toString());
            Map<String, String> options = new HashMap<String, String>();
            options.put("replication_factor", "1");
            ksDef.setStrategy_options(options);
        } else {
            ksDef.setStrategy_class(strategyClass.toString());
            ksDef.setStrategy_options(strategyOptions);
        }
        List<CfDef> list = new ArrayList<CfDef>();

        if (columnNames != null && !columnNames.isEmpty()) {
            list = CassandraDBUtils.toColumnDefinition(columnNames, keyspaceName);
        }
        ksDef.setCf_defs(list);
        return this.systemAddKeyspaceFromObject(ksDef);
    }

    /**
     * Drops a keyspace. Creates a snapshot and then submits a 'graveyard'
     * compaction during which the abandoned files will be deleted.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:system-drop-keyspace}
     *
     * @param keyspace Name of the keyspace to be dropped
     * @return The new schema version ID.
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public Object systemDropKeyspace(String keyspace)
            throws CassandraDBException {
        try {
            return client.system_drop_keyspace(keyspace);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (SchemaDisagreementException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Updates properties of a keyspace.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:system-update-keyspace}
     *
     * @param keyspaceDef New keyspace to be applied for update
     * @return The new schema version ID.
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public String systemUpdateKeyspace(KsDef keyspaceDef)
            throws CassandraDBException {
        try {
            return client.system_update_keyspace(keyspaceDef);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (SchemaDisagreementException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Updates properties of a ColumnFamily.
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:system-update-column-family}
     *
     * @param columnFamily CfDef with new settings
     * @return The new schema version ID.
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public String systemUpdateColumnFamily(CfDef columnFamily)
            throws CassandraDBException {
        try {
            return client.system_update_column_family(columnFamily);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (SchemaDisagreementException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * Executes a CQL (Cassandra Query Language) statement and returns a
     * CqlResult containing the results. For more information about CQL please visit: http://cassandra.apache.org/doc/cql/CQL.html
     * <p/>
     * {@sample.xml ../../../doc/CassandraDB-connector.xml.sample
     * cassandradb:execute-cql-query}
     *
     * @param query       CQL Statement to be executed
     * @param compression Compression level, by default we use NONE
     * @return CqlResult containing the results of the execution
     * @throws com.mulesoft.mule.cassandradb.CassandraDBException Generic Exception wrapper class for Thrift Exceptions.
     */
    @Processor
    public Object executeCqlQuery(String query, @Default("NONE") Compression compression)
            throws CassandraDBException {
        try {
            return client.execute_cql_query(CassandraDBUtils.toByteBuffer(query),
                    compression);
        } catch (InvalidRequestException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (UnavailableException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TimedOutException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (SchemaDisagreementException e) {
            throw new CassandraDBException(e.getMessage(), e);
        } catch (TException e) {
            throw new CassandraDBException(e.getMessage(), e);
        }
    }

    /**
     * @return the host connection url.
     */
    public String getHost() {
        return this.host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Retrieves the Port.
     *
     * @return the connection port.
     */
    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Retrieves the Keyspace.
     *
     * @return the keyspace.
     */
    public String getKeyspace() {
        return this.keyspace;
    }

    /**
     * Set the keyspace
     *
     * @param keyspace set the keyspace.
     */
    public void setKeyspace(String keyspace) {
        this.keyspace = keyspace;
    }

    /**
     * Retrieves the Cassandra DB consistency level.
     *
     * @return the consistency level.
     */
    public ConsistencyLevel getConsistencyLevel() {
        return this.consistencyLevel;
    }

    /**
     * Set the consistency level.
     *
     * @param consistencyLevel set the consistency level.
     */
    public void setConsistencyLevel(ConsistencyLevel consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
    }

    public void setClient(Cassandra.Client client) {
        this.client = client;
    }
}
