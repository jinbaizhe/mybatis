/*
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.session;

/**
 * 执行器类型
 * @author Clinton Begin
 */
public enum ExecutorType {

  /**
   * 默认的执行器，对每条SQL进行预处理（预编译）、填充参数、执行等操作。
   * @see Configuration#defaultExecutorType
   */
  SIMPLE,

  /**
   * 重用预处理语句：如果这条SQL之前已经被编译过了，本次就不再编译，直接取出去填充参数。
   */
  REUSE,

  /**
   * 批量处理：对相同的SQL只执行一次预编译，并且在最后会统一去执行SQL。
   */
  BATCH
}
