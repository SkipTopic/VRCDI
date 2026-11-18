package com.vrcdevicecheck.osc.weather;

/**
 * 天气获取过程的实时进度回调。
 *
 * 回调在后台线程触发，实现方自己负责切回主线程。
 */
public interface WeatherFetchListener {

    /** 一行进度或结果，例如「① 连接 Open-Meteo…」「✗ 失败：连接超时」 */
    void onStep(String line);
}
