/**
 * HTTP 接口入口层。
 *
 * <p>Controller 只做四件事：接收参数、触发校验、取得当前用户、调用 Service 并包装
 * {@code ApiResponse}。业务规则、事务和数据库操作不应写在这一层。</p>
 *
 * <p>阅读某个接口时，先找对应 Controller 的路径注解，再沿着注入的 Service 接口继续向下读。</p>
 */
package com.hm.badminton.controller;
