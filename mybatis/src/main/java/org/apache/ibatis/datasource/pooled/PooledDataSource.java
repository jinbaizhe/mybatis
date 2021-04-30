/*
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.datasource.pooled;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * This is a simple, synchronous, thread-safe database connection pool.
 *
 * 数据库连接池
 * @author Clinton Begin
 */
public class PooledDataSource implements DataSource {

  private static final Log log = LogFactory.getLog(PooledDataSource.class);

  /**
   * 记录数据源的状态信息
   */
  private final PoolState state = new PoolState(this);

  /**
   * 委托，实例创建连接时，还是要通过非池化的数据源的
   */
  private final UnpooledDataSource dataSource;

  // OPTIONAL CONFIGURATION FIELDS
  /**
   * 最大活动连接数量
   */
  protected int poolMaximumActiveConnections = 10;

  /**
   * 最大空闲连接数量
   */
  protected int poolMaximumIdleConnections = 5;

  /**
   * 强制回收连接的时间阈值：当一个连接被使用超过该时间时，会被强制回收。
   */
  protected int poolMaximumCheckoutTime = 20000;

  /**
   * 最大等待获取连接时间
   */
  protected int poolTimeToWait = 20000;

  /**
   * 最大坏连接容忍度，默认是3次
   * 每次尝试获取连接时，最多重试 poolMaximumIdleConnections + poolMaximumLocalBadConnectionTolerance 次
   */
  protected int poolMaximumLocalBadConnectionTolerance = 3;

  /**
   * 发送到数据库的侦测查询，用来检验连接是否正常工作并准备接受请求。
   * 默认是“NO PING QUERY SET”，故意让数据库在执行该命令时报错，让其返回错误消息。
   */
  protected String poolPingQuery = "NO PING QUERY SET";

  /**
   * 是否启用侦测查询。
   * 若开启，需要设置 poolPingQuery 属性为一个可执行的 SQL 语句（最好是一个速度非常快的 SQL 语句）。
   * 默认值：false。
   */
  protected boolean poolPingEnabled;

  /**
   * 执行 poolPingQuery 的频率。
   * 可以被设置为和数据库连接超时时间一样，来避免不必要的侦测。
   * 默认值：0（即所有连接每一时刻都被侦测 — 当然仅当 poolPingEnabled 为 true 时适用）。
   */
  protected int poolPingConnectionsNotUsedFor;

  private int expectedConnectionTypeCode;

  public PooledDataSource() {
    dataSource = new UnpooledDataSource();
  }

  public PooledDataSource(UnpooledDataSource dataSource) {
    this.dataSource = dataSource;
  }

  public PooledDataSource(String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driver, url, username, password);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driver, url, driverProperties);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, username, password);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, driverProperties);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  @Override
  public Connection getConnection() throws SQLException {
    return popConnection(dataSource.getUsername(), dataSource.getPassword()).getProxyConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return popConnection(username, password).getProxyConnection();
  }

  @Override
  public void setLoginTimeout(int loginTimeout) {
    DriverManager.setLoginTimeout(loginTimeout);
  }

  @Override
  public int getLoginTimeout() {
    return DriverManager.getLoginTimeout();
  }

  @Override
  public void setLogWriter(PrintWriter logWriter) {
    DriverManager.setLogWriter(logWriter);
  }

  @Override
  public PrintWriter getLogWriter() {
    return DriverManager.getLogWriter();
  }

  public void setDriver(String driver) {
    dataSource.setDriver(driver);
    forceCloseAll();
  }

  public void setUrl(String url) {
    dataSource.setUrl(url);
    forceCloseAll();
  }

  public void setUsername(String username) {
    dataSource.setUsername(username);
    forceCloseAll();
  }

  public void setPassword(String password) {
    dataSource.setPassword(password);
    forceCloseAll();
  }

  public void setDefaultAutoCommit(boolean defaultAutoCommit) {
    dataSource.setAutoCommit(defaultAutoCommit);
    forceCloseAll();
  }

  public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
    dataSource.setDefaultTransactionIsolationLevel(defaultTransactionIsolationLevel);
    forceCloseAll();
  }

  public void setDriverProperties(Properties driverProps) {
    dataSource.setDriverProperties(driverProps);
    forceCloseAll();
  }

  /**
   * Sets the default network timeout value to wait for the database operation to complete. See {@link Connection#setNetworkTimeout(java.util.concurrent.Executor, int)}
   *
   * @param milliseconds
   *          The time in milliseconds to wait for the database operation to complete.
   * @since 3.5.2
   */
  public void setDefaultNetworkTimeout(Integer milliseconds) {
    dataSource.setDefaultNetworkTimeout(milliseconds);
    forceCloseAll();
  }

  /**
   * The maximum number of active connections.
   *
   * @param poolMaximumActiveConnections
   *          The maximum number of active connections
   */
  public void setPoolMaximumActiveConnections(int poolMaximumActiveConnections) {
    this.poolMaximumActiveConnections = poolMaximumActiveConnections;
    forceCloseAll();
  }

  /**
   * The maximum number of idle connections.
   *
   * @param poolMaximumIdleConnections
   *          The maximum number of idle connections
   */
  public void setPoolMaximumIdleConnections(int poolMaximumIdleConnections) {
    this.poolMaximumIdleConnections = poolMaximumIdleConnections;
    forceCloseAll();
  }

  /**
   * The maximum number of tolerance for bad connection happens in one thread
   * which are applying for new {@link PooledConnection}.
   *
   * @param poolMaximumLocalBadConnectionTolerance
   *          max tolerance for bad connection happens in one thread
   *
   * @since 3.4.5
   */
  public void setPoolMaximumLocalBadConnectionTolerance(
      int poolMaximumLocalBadConnectionTolerance) {
    this.poolMaximumLocalBadConnectionTolerance = poolMaximumLocalBadConnectionTolerance;
  }

  /**
   * The maximum time a connection can be used before it *may* be
   * given away again.
   *
   * @param poolMaximumCheckoutTime
   *          The maximum time
   */
  public void setPoolMaximumCheckoutTime(int poolMaximumCheckoutTime) {
    this.poolMaximumCheckoutTime = poolMaximumCheckoutTime;
    forceCloseAll();
  }

  /**
   * The time to wait before retrying to get a connection.
   *
   * @param poolTimeToWait
   *          The time to wait
   */
  public void setPoolTimeToWait(int poolTimeToWait) {
    this.poolTimeToWait = poolTimeToWait;
    forceCloseAll();
  }

  /**
   * The query to be used to check a connection.
   *
   * @param poolPingQuery
   *          The query
   */
  public void setPoolPingQuery(String poolPingQuery) {
    this.poolPingQuery = poolPingQuery;
    forceCloseAll();
  }

  /**
   * Determines if the ping query should be used.
   *
   * @param poolPingEnabled
   *          True if we need to check a connection before using it
   */
  public void setPoolPingEnabled(boolean poolPingEnabled) {
    this.poolPingEnabled = poolPingEnabled;
    forceCloseAll();
  }

  /**
   * If a connection has not been used in this many milliseconds, ping the
   * database to make sure the connection is still good.
   *
   * @param milliseconds
   *          the number of milliseconds of inactivity that will trigger a ping
   */
  public void setPoolPingConnectionsNotUsedFor(int milliseconds) {
    this.poolPingConnectionsNotUsedFor = milliseconds;
    forceCloseAll();
  }

  public String getDriver() {
    return dataSource.getDriver();
  }

  public String getUrl() {
    return dataSource.getUrl();
  }

  public String getUsername() {
    return dataSource.getUsername();
  }

  public String getPassword() {
    return dataSource.getPassword();
  }

  public boolean isAutoCommit() {
    return dataSource.isAutoCommit();
  }

  public Integer getDefaultTransactionIsolationLevel() {
    return dataSource.getDefaultTransactionIsolationLevel();
  }

  public Properties getDriverProperties() {
    return dataSource.getDriverProperties();
  }

  /**
   * Gets the default network timeout.
   *
   * @return the default network timeout
   * @since 3.5.2
   */
  public Integer getDefaultNetworkTimeout() {
    return dataSource.getDefaultNetworkTimeout();
  }

  public int getPoolMaximumActiveConnections() {
    return poolMaximumActiveConnections;
  }

  public int getPoolMaximumIdleConnections() {
    return poolMaximumIdleConnections;
  }

  public int getPoolMaximumLocalBadConnectionTolerance() {
    return poolMaximumLocalBadConnectionTolerance;
  }

  public int getPoolMaximumCheckoutTime() {
    return poolMaximumCheckoutTime;
  }

  public int getPoolTimeToWait() {
    return poolTimeToWait;
  }

  public String getPoolPingQuery() {
    return poolPingQuery;
  }

  public boolean isPoolPingEnabled() {
    return poolPingEnabled;
  }

  public int getPoolPingConnectionsNotUsedFor() {
    return poolPingConnectionsNotUsedFor;
  }

  /**
   * Closes all active and idle connections in the pool.
   * 关闭所有连接（所有活跃或空闲的连接）
   */
  public void forceCloseAll() {

    //对数据源状态，加重量级锁
    synchronized (state) {

      //封装连接的信息，并返回hashCode
      expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());

      //遍历活跃连接列表，并关闭对应的连接
      for (int i = state.activeConnections.size(); i > 0; i--) {
        try {

          //从活跃连接列表中移除
          PooledConnection conn = state.activeConnections.remove(i - 1);

          //标示该连接已无效
          conn.invalidate();

          //获取真正的连接
          Connection realConn = conn.getRealConnection();

          //如果该设置了不自动提交，就直接执行事务回滚操作
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }

          //对真正的连接进行关闭操作
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }

      //遍历空闲连接列表，并关闭对应的连接
      for (int i = state.idleConnections.size(); i > 0; i--) {
        try {

          //从空闲连接列表中移除
          PooledConnection conn = state.idleConnections.remove(i - 1);

          //标示该连接已无效
          conn.invalidate();

          //获取真正的连接
          Connection realConn = conn.getRealConnection();

          //如果该设置了不自动提交，就直接执行事务回滚操作
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }

          //对真正的连接进行关闭操作
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("PooledDataSource forcefully closed/removed all connections.");
    }
  }

  public PoolState getPoolState() {
    return state;
  }

  /**
   * 封装连接的信息，并返回hashCode
   * @param url
   * @param username
   * @param password
   * @return
   */
  private int assembleConnectionTypeCode(String url, String username, String password) {
    return ("" + url + username + password).hashCode();
  }

  /**
   * 归还连接到连接池中
   * @param conn
   * @throws SQLException
   */
  protected void pushConnection(PooledConnection conn) throws SQLException {
    //对数据源状态对象加重量级锁，保证线程安全
    synchronized (state) {

      //先从活跃连接列表中移除
      state.activeConnections.remove(conn);

      //如果该连接还是有效的
      if (conn.isValid()) {

        //如果当前空闲连接数量小于最大空闲数量，并且这个连接是属于当前这个数据源的
        if (state.idleConnections.size() < poolMaximumIdleConnections && conn.getConnectionTypeCode() == expectedConnectionTypeCode) {

          //统计信息，增加连接的累计使用时间
          state.accumulatedCheckoutTime += conn.getCheckoutTime();

          //如果连接设置了不自动提交，就直接执行回滚事务操作
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }

          //取出真实的连接，并重新包装到PooledConnection对象中
          PooledConnection newConn = new PooledConnection(conn.getRealConnection(), this);

          //添加到空闲连接列表中
          state.idleConnections.add(newConn);

          //设置该连接的创建时间、上次使用时间信息
          newConn.setCreatedTimestamp(conn.getCreatedTimestamp());
          newConn.setLastUsedTimestamp(conn.getLastUsedTimestamp());

          //对老连接进行标记失效
          conn.invalidate();

          if (log.isDebugEnabled()) {
            log.debug("Returned connection " + newConn.getRealHashCode() + " to pool.");
          }

          //notify操作，通知那些还在wait的线程（正在等待获取连接的线程）
          state.notifyAll();

        } else {
          //当前空闲连接数大于等于最大空闲数量的情况

          //统计信息，累计使用时间
          state.accumulatedCheckoutTime += conn.getCheckoutTime();

          //如果连接设置了不自动提交，则直接进行事务回滚操作
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }

          //对连接进行关闭
          conn.getRealConnection().close();
          if (log.isDebugEnabled()) {
            log.debug("Closed connection " + conn.getRealHashCode() + ".");
          }

          //标记该连接是无效的
          conn.invalidate();
        }
      } else {
        //该连接是无效的情况

        if (log.isDebugEnabled()) {
          log.debug("A bad connection (" + conn.getRealHashCode() + ") attempted to return to the pool, discarding connection.");
        }

        //统计信息，坏连接数量+1
        state.badConnectionCount++;
      }
    }
  }

  /**
   * 获取一个连接
   * @param username
   * @param password
   * @return
   * @throws SQLException
   */
  private PooledConnection popConnection(String username, String password) throws SQLException {
    boolean countedWait = false;

    //连接
    PooledConnection conn = null;

    long t = System.currentTimeMillis();

    //统计为了获取连接，已经尝试了几次
    int localBadConnectionCount = 0;

    //循环去获取连接
    while (conn == null) {
      //对PoolState加重量级锁，保证在并发获取连接，修改数据源状态时，不存在线程安全问题
      synchronized (state) {
        //当存在空闲连接时
        if (!state.idleConnections.isEmpty()) {
          // Pool has available connection
          //取出第一个
          conn = state.idleConnections.remove(0);
          if (log.isDebugEnabled()) {
            log.debug("Checked out connection " + conn.getRealHashCode() + " from pool.");
          }
        } else {
          // Pool does not have available connection
          //不存在空闲连接时

          //当前活动的连接数小于最大活动连接数时
          if (state.activeConnections.size() < poolMaximumActiveConnections) {
            // Can create new connection
            //直接创建一个新的连接：从UnpooledDataSource中创建一个连接，并封装在PooledConnection类中。
            conn = new PooledConnection(dataSource.getConnection(), this);
            if (log.isDebugEnabled()) {
              log.debug("Created connection " + conn.getRealHashCode() + ".");
            }
          } else {
            // Cannot create new connection
            //当前活动的连接数等于最大激活连接数时
            //取出第一个活动连接（活动时间最长的一个连接），FIFO
            PooledConnection oldestActiveConnection = state.activeConnections.get(0);
            //拿到当前连接的使用时间
            long longestCheckoutTime = oldestActiveConnection.getCheckoutTime();
            //当该连接的使用时间大于最大回收时间时
            if (longestCheckoutTime > poolMaximumCheckoutTime) {
              // Can claim overdue connection
              //说明这是一个过期的连接

              //统计信息（记录连接池的数据指标）。过期连接数+1
              state.claimedOverdueConnectionCount++;

              //统计信息，累计的过期连接的使用时间
              state.accumulatedCheckoutTimeOfOverdueConnections += longestCheckoutTime;

              //统计时间，累计的连接使用时间
              state.accumulatedCheckoutTime += longestCheckoutTime;

              //把该连接从活动连接列表中移除（回收）
              state.activeConnections.remove(oldestActiveConnection);

              //如果设置了非自动提交
              if (!oldestActiveConnection.getRealConnection().getAutoCommit()) {
                try {
                  //对过期连接执行回滚操作
                  oldestActiveConnection.getRealConnection().rollback();
                } catch (SQLException e) {
                  /*
                     Just log a message for debug and continue to execute the following
                     statement like nothing happened.
                     Wrap the bad connection with a new PooledConnection, this will help
                     to not interrupt current executing thread and give current thread a
                     chance to join the next competition for another valid/good database
                     connection. At the end of this loop, bad {@link @conn} will be set as null.
                   */
                  log.debug("Bad connection. Could not roll back");
                }
              }

              //创建一个新的连接
              conn = new PooledConnection(oldestActiveConnection.getRealConnection(), this);

              //设置连接对创建时间和上次使用时间
              conn.setCreatedTimestamp(oldestActiveConnection.getCreatedTimestamp());
              conn.setLastUsedTimestamp(oldestActiveConnection.getLastUsedTimestamp());

              //失效过期的连接
              oldestActiveConnection.invalidate();
              if (log.isDebugEnabled()) {
                log.debug("Claimed overdue connection " + conn.getRealHashCode() + ".");
              }
            } else {
              // Must wait
              //该连接的使用时间，未达到最大可回收时间

              try {
                if (!countedWait) {

                  //统计信息，等待获取连接的次数+1
                  state.hadToWaitCount++;
                  countedWait = true;
                }
                if (log.isDebugEnabled()) {
                  log.debug("Waiting as long as " + poolTimeToWait + " milliseconds for connection.");
                }
                long wt = System.currentTimeMillis();

                //一直阻塞等待，直到连接可用或者等待时间达到最大等待时间
                state.wait(poolTimeToWait);

                //统计信息，计算累计等待时间
                state.accumulatedWaitTime += System.currentTimeMillis() - wt;
              } catch (InterruptedException e) {
                break;
              }
            }
          }
        }
        if (conn != null) {
          // ping to server and check the connection is valid or not
          //尝试ping一下数据库服务器，判断连接是否正常
          if (conn.isValid()) {

            //如果该连接不是自动提交的，就先回滚上一个事务
            if (!conn.getRealConnection().getAutoCommit()) {
              conn.getRealConnection().rollback();
            }

            //拼接数据库连接地址、账号、密码成字符串，最后再取hashCode
            conn.setConnectionTypeCode(assembleConnectionTypeCode(dataSource.getUrl(), username, password));
            conn.setCheckoutTimestamp(System.currentTimeMillis());
            conn.setLastUsedTimestamp(System.currentTimeMillis());

            //把该连接添加到活跃连接列表中
            state.activeConnections.add(conn);

            //统计信息，请求获取连接数量+1
            state.requestCount++;

            //计算累计获取连接所需的时间
            state.accumulatedRequestTime += System.currentTimeMillis() - t;
          } else {
            //该连接无法正常工作
            if (log.isDebugEnabled()) {
              log.debug("A bad connection (" + conn.getRealHashCode() + ") was returned from the pool, getting another connection.");
            }

            //统计信息，坏连接数量+1
            state.badConnectionCount++;

            //统计为了获取连接，已经尝试了几次
            localBadConnectionCount++;

            //尝试通过下一次循环遍历去获取
            conn = null;

            //如果重新尝试获取连接的次数，
            //大于 最大空闲连接数+坏连接容忍度 （poolMaximumIdleConnections + poolMaximumLocalBadConnectionTolerance之和，
            //则抛出"获取不到连接"的异常
            if (localBadConnectionCount > (poolMaximumIdleConnections + poolMaximumLocalBadConnectionTolerance)) {
              if (log.isDebugEnabled()) {
                log.debug("PooledDataSource: Could not get a good connection to the database.");
              }
              throw new SQLException("PooledDataSource: Could not get a good connection to the database.");
            }
          }
        }
      }

    }

    if (conn == null) {
      if (log.isDebugEnabled()) {
        log.debug("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
      }
      throw new SQLException("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
    }

    return conn;
  }

  /**
   * Method to check to see if a connection is still usable
   *
   * 执行指定的ping命令。判断连接是否可用
   * @param conn
   *          - the connection to check
   * @return True if the connection is still usable
   */
  protected boolean pingConnection(PooledConnection conn) {
    boolean result = true;

    //查询该连接是否已关闭
    try {
      result = !conn.getRealConnection().isClosed();
    } catch (SQLException e) {
      if (log.isDebugEnabled()) {
        log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
      }
      result = false;
    }

    //如果该连接未关闭，且开启了使用ping命令去尝试执行，并且在执行ping检查的频率之内
    if (result && poolPingEnabled && poolPingConnectionsNotUsedFor >= 0
        && conn.getTimeElapsedSinceLastUse() > poolPingConnectionsNotUsedFor) {
      try {
        if (log.isDebugEnabled()) {
          log.debug("Testing connection " + conn.getRealHashCode() + " ...");
        }

        //取出真正的连接对象
        Connection realConn = conn.getRealConnection();

        //创建Statement，去执行ping命令
        try (Statement statement = realConn.createStatement()) {
          statement.executeQuery(poolPingQuery).close();
        }

        //如果是在一个事务里，就先执行回滚操作，保证不影响之后的SQL操作
        if (!realConn.getAutoCommit()) {
          realConn.rollback();
        }

        //此时可以判断出，该连接是可用的
        result = true;

        if (log.isDebugEnabled()) {
          log.debug("Connection " + conn.getRealHashCode() + " is GOOD!");
        }
      } catch (Exception e) {
        log.warn("Execution of ping query '" + poolPingQuery + "' failed: " + e.getMessage());

        //发生异常时，强行关闭该连接
        try {
          conn.getRealConnection().close();
        } catch (Exception e2) {
          // ignore
        }

        //此时该连接是不可用的
        result = false;
        if (log.isDebugEnabled()) {
          log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
        }
      }
    }
    return result;
  }

  /**
   * Unwraps a pooled connection to get to the 'real' connection
   *
   * 获得真正的连接对象
   * @param conn
   *          - the pooled connection to unwrap
   * @return The 'real' connection
   */
  public static Connection unwrapConnection(Connection conn) {
    if (Proxy.isProxyClass(conn.getClass())) {
      InvocationHandler handler = Proxy.getInvocationHandler(conn);
      if (handler instanceof PooledConnection) {
        return ((PooledConnection) handler).getRealConnection();
      }
    }
    return conn;
  }

  @Override
  protected void finalize() throws Throwable {
    forceCloseAll();
    super.finalize();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException(getClass().getName() + " is not a wrapper.");
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) {
    return false;
  }

  @Override
  public Logger getParentLogger() {
    return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  }

}
