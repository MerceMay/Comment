package com.mercemay.comment.utils;

public interface ILock {
    /**
     * 获取锁
     *
     * @param timeoutSec 超时时间，单位秒·
     * @return 是否获取成功
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
