/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.oauth2.device.dao;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.device.constants.Constants;
import org.wso2.carbon.identity.oauth2.device.model.DeviceFlowDO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.UUID;

/**
 * This class contains override methods of DeviceFlowDAO.
 */
public class DeviceFlowDAOImpl implements DeviceFlowDAO {

    private static final Log log = LogFactory.getLog(DeviceFlowDAOImpl.class);

    private String clientId;
    private String status;
    private String scope;

    @Override
    public void insertDeviceFlow(String deviceCode, String userCode, String consumerKey, String scope, Long expiresIn,
                                 int interval) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection(true);
        PreparedStatement prepStmt = null;
        try {
            Date date = new Date();
            String sql;
            sql = SQLQueries.DeviceFlowDAOSQLQueries.STORE_DEVICE_CODE;
            Timestamp timeCreated = new Timestamp(date.getTime());
            long timeExpired = timeCreated.getTime() + expiresIn;
            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, UUID.randomUUID().toString());
            prepStmt.setString(2, deviceCode);
            prepStmt.setString(3, userCode);
            prepStmt.setString(4, scope);
            prepStmt.setTimestamp(5, timeCreated, Calendar.getInstance(TimeZone
                    .getTimeZone(Constants.UTC)));
            prepStmt.setTimestamp(6, timeCreated, Calendar.getInstance(TimeZone
                    .getTimeZone(Constants.UTC)));
            prepStmt.setLong(7, timeExpired);
            prepStmt.setLong(8, interval);
            prepStmt.setString(9, Constants.PENDING);
            prepStmt.setString(10, consumerKey);
            prepStmt.execute();
            IdentityDatabaseUtil.commitTransaction(connection);

        } catch (SQLException e) {
            IdentityDatabaseUtil.rollbackTransaction(connection);
            throw new IdentityOAuth2Exception("Error when storing the device flow parameters for consumer key : " +
                    consumerKey, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, prepStmt);
        }
    }

    @Override
    public String getClientIdByUserCode(String userCode) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection(false);
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            String sqlQuery = SQLQueries.DeviceFlowDAOSQLQueries.GET_CONSUMER_KEY_FOR_USER_CODE;
            ps = connection.prepareStatement(sqlQuery);
            ps.setString(1, userCode);
            rs = ps.executeQuery();

            while (rs.next()) {
                clientId = rs.getString(1);
            }

        } catch (SQLException e) {
            throw new IdentityOAuth2Exception("Error when getting client id for user code : " +
                    userCode, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, ps);
        }
        return clientId;
    }

    @Override
    public void setUserAuthenticated(String userCode, String status) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection(true);
        PreparedStatement prepStmt = null;
        try {
            String sql;
            sql = SQLQueries.DeviceFlowDAOSQLQueries.SET_USER_HAS_AUTHENTICATED;
            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, status);
            prepStmt.setString(2, userCode);
            prepStmt.execute();
            IdentityDatabaseUtil.commitTransaction(connection);
        } catch (SQLException e) {
            IdentityDatabaseUtil.rollbackTransaction(connection);
            throw new IdentityOAuth2Exception("Error when setting user has authenticated for user code :" +
                    userCode, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, prepStmt);
        }
    }

    @Override
    public String getClientIdByDeviceCode(String deviceCode) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection(false);
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            String sqlQuery = SQLQueries.DeviceFlowDAOSQLQueries.GET_CONSUMER_KEY_FOR_DEVICE_CODE;
            ps = connection.prepareStatement(sqlQuery);
            ps.setString(1, deviceCode);
            rs = ps.executeQuery();

            while (rs.next()) {
                clientId = rs.getString(1);
            }

        } catch (SQLException e) {
            throw new IdentityOAuth2Exception("Error when getting client id for device code : " +
                    deviceCode, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, rs, ps);
        }
        return clientId;
    }

    @Override
    public DeviceFlowDO getAuthenticationStatus(String deviceCode) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection(false);
        PreparedStatement ps = null;
        ResultSet rs = null;
        boolean checked = false;
        HashMap<String, String> result = new HashMap<>();
        DeviceFlowDO deviceFlowDO = new DeviceFlowDO();
        try {
            String sqlQuery = SQLQueries.DeviceFlowDAOSQLQueries.GET_AUTHENTICATION_STATUS;
            ps = connection.prepareStatement(sqlQuery);
            ps.setString(1, deviceCode);
            rs = ps.executeQuery();

            while (rs.next()) {
                try {
                    deviceFlowDO.setStatus(rs.getString(1));
                    deviceFlowDO.setLastPollTime(rs.getTimestamp(2));
                    deviceFlowDO.setPollTime(rs.getLong(3));
                    deviceFlowDO.setExpiryTime(rs.getLong(4));
                    deviceFlowDO.setScope(rs.getString(5));
                    deviceFlowDO.setAuthzUser(rs.getString(6));
                    checked = true;
                } catch (NullPointerException e) {
                    deviceFlowDO.setStatus(Constants.NOT_EXIST);
                    return deviceFlowDO;
                }
            }

        } catch (SQLException e) {
            throw new IdentityOAuth2Exception("Error when getting authentication status for device code : " +
                    deviceCode, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, rs, ps);
        }
        if (checked) {
            return deviceFlowDO;
        } else {
            deviceFlowDO.setStatus(Constants.NOT_EXIST);
            return deviceFlowDO;
        }
    }

    @Override
    public boolean checkClientIdExist(String clientId) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection(false);
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            String sqlQuery = SQLQueries.DeviceFlowDAOSQLQueries.CHECK_CLIENT_ID_EXISTS;
            ps = connection.prepareStatement(sqlQuery);
            ps.setString(1, clientId);
            rs = ps.executeQuery();
            while (rs.next()) {
                status = rs.getString(1);
                if (status != null) {
                    return true;
                } else {
                    return false;
                }
            }
        } catch (SQLException e) {
            throw new IdentityOAuth2Exception("Error when getting client id : " +
                    clientId, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, rs, ps);
        }
        return false;
    }

    @Override
    public String getScopeForDevice(String userCode) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection(false);
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            String sqlQuery = SQLQueries.DeviceFlowDAOSQLQueries.GET_SCOPE_FOR_USER_CODE;
            ps = connection.prepareStatement(sqlQuery);
            ps.setString(1, userCode);
            rs = ps.executeQuery();
            while (rs.next()) {
                scope = rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IdentityOAuth2Exception("Error when getting scopes for user code : " +
                    userCode, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, rs, ps);
        }
        return scope;
    }

    @Override
    public String getStatusForUserCode(String userCode) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection(false);
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            String sqlQuery = SQLQueries.DeviceFlowDAOSQLQueries.GET_USER_CODE_STATUS;
            ps = connection.prepareStatement(sqlQuery);
            ps.setString(1, userCode);
            rs = ps.executeQuery();
            while (rs.next()) {
                status = rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IdentityOAuth2Exception("Error when getting status for user code : " +
                    userCode, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, rs, ps);
        }
        return status;
    }

    @Override
    public void setLastPollTime(String deviceCode, Timestamp newPOllTime) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection();
        PreparedStatement ps = null;
        try {
            String sql;
            sql = SQLQueries.DeviceFlowDAOSQLQueries.SET_LAST_POLL_TIME;
            ps = connection.prepareStatement(sql);
            ps.setTimestamp(1, newPOllTime);
            ps.setString(2, deviceCode);
            ps.execute();
            IdentityDatabaseUtil.commitTransaction(connection);
        } catch (SQLException e) {
            throw new IdentityOAuth2Exception("Error when setting last poll time", e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, ps);
        }
    }

    @Override
    public void setAuthzUser(String userCode, String userName) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection(true);
        PreparedStatement ps = null;
        try {
            String sql;
            sql = SQLQueries.DeviceFlowDAOSQLQueries.SET_AUTHZ_USER;
            ps = connection.prepareStatement(sql);
            ps.setString(1, userName);
            ps.setString(2, userCode);
            ps.execute();
            IdentityDatabaseUtil.commitTransaction(connection);
        } catch (SQLException e) {
            IdentityDatabaseUtil.rollbackTransaction(connection);
            throw new IdentityOAuth2Exception("Error when setting authenticated user for user code : " +
                    userCode, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, ps);
        }
    }

    @Override
    public void setDeviceCodeExpired(String deviceCode, String status) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection(true);
        PreparedStatement prepStmt = null;
        try {
            String sql;
            sql = SQLQueries.DeviceFlowDAOSQLQueries.SET_DEVICE_CODE_EXPIRED;
            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, status);
            prepStmt.setString(2, deviceCode);
            prepStmt.execute();
            IdentityDatabaseUtil.commitTransaction(connection);
        } catch (SQLException e) {
            IdentityDatabaseUtil.rollbackTransaction(connection);
            throw new IdentityOAuth2Exception("Error when setting expired status for device code : " +
                    deviceCode, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, prepStmt);
        }
    }

    @Override
    public void setCallBackURI(String clientId, String callBackUri) throws IdentityOAuth2Exception {

        Connection connection = IdentityDatabaseUtil.getDBConnection();
        PreparedStatement prepStmt = null;
        try {
            String sql;
            sql = SQLQueries.DeviceFlowDAOSQLQueries.SET_CALLBACK_URI;
            prepStmt = connection.prepareStatement(sql);
            prepStmt.setString(1, callBackUri);
            prepStmt.setString(2, clientId);
            prepStmt.execute();
            IdentityDatabaseUtil.commitTransaction(connection);
        } catch (SQLException e) {
            throw new IdentityOAuth2Exception("Error when setting expired callBackUri for consumer key: " +
                    clientId, e);
        } finally {
            IdentityDatabaseUtil.closeAllConnections(connection, null, prepStmt);
        }
    }

}
