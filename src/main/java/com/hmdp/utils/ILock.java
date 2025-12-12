package com.hmdp.utils;

/**
 * ILock
 *
 * @Description
 * @Author hanchenyang
 * @Date 2025/12/2 16:41
 */
public interface ILock {

    boolean tryLock(Long timeoutSec);

    void unLock();
}
