package com.red5pro.service;

/**
 * Common interface for Red5 Pro Service implementations.
 *
 * @author Paul Gregoire
 *
 */
public interface IRed5ProService {

    String getName();

    boolean start();

    boolean stop();

}
