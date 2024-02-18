package com.red5pro.util;

import java.util.Arrays;
import java.util.Optional;

import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScopeUtil {

    private final static Logger log = LoggerFactory.getLogger(ScopeUtil.class);

    private final static String SLASH = "/";

    /**
     * Resolves the path against a given scope. An attempt to create child scopes is performed when they don't already
     * exist and all the previous steps complete. Be aware that this utility method only expects to operate on Application
     * and Room scopes; scopes implementing Shared Object or anything additional will be checked and possibly cleaned up
     * to prevent stale or disassociated scopes.
     *
     * @param from parent scope
     * @param path child path(s)
     * @return IScope or null if logic fails
     */
    public final static IScope resolveScope(IScope from, String path) {
        return ScopeUtil.resolveScope(from, path, true, true);
    }

    /**
     * Resolves the path against a given scope. An attempt to create child scopes is performed when they don't already
     * exist and all the previous steps complete. Be aware that this utility method only expects to operate on Application
     * and Room scopes; scopes implementing Shared Object or anything additional will be checked and possibly cleaned up
     * to prevent stale or disassociated scopes.
     *
     * @param from parent scope
     * @param path child path(s)
     * @param createIfAbsent create scopes that are missing
     * @param removeDangling check for and remove dangling scopes
     * @return IScope or null if logic fails
     */
    public final static IScope resolveScope(IScope from, String path, boolean createIfAbsent, boolean removeDangling) {
        log.debug("resolveScope from: {} path: {}", from.getName(), path);
        IScope current = from;
        if (path.startsWith(SLASH)) {
            current = ScopeUtils.findRoot(current);
            path = path.substring(1, path.length());
        }
        if (path.endsWith(SLASH)) {
            path = path.substring(0, path.length() - 1);
        }
        log.trace("Current: {}", current);
        String[] parts = path.split(SLASH);
        if (log.isTraceEnabled()) {
            log.trace("Parts: {}", Arrays.toString(parts));
        }
        for (String part : parts) {
            log.trace("Part: {}", part);
            if (part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!current.hasParent()) {
                    return null;
                }
                current = current.getParent();
                continue;
            }
            if (!current.hasChildScope(part)) {
                // if a creation request was include
                if (createIfAbsent) {
                    if (!current.createChildScope(part)) {
                        log.debug("Could not create child scope: {}", current);
                        return null;
                    } else {
                        // set current to newly created child scope
                        current = current.getScope(part);
                        // allow additional child scopes to be created
                        continue;
                    }
                }
                log.debug("Child scope: {} doesnt exist on: {}", current);
                return null;
            }
            // try ROOM type first then fallback to APPLICATION type
            IScope appOrRoomScope = Optional.ofNullable((IScope) current.getBasicScope(ScopeType.ROOM, part))
                    .orElse((IScope) current.getBasicScope(ScopeType.APPLICATION, part));
            // if we get an application or room scope back, set it as current, otherwise return null
            if (appOrRoomScope != null) {
                current = appOrRoomScope;
            } else if (removeDangling) {
                // clean up the possibly dangling scope
                Optional<IBasicScope> opt = Optional.ofNullable(current.getScope(part));
                if (opt.isPresent()) {
                    IBasicScope danglingScope = opt.get();
                    log.debug("Removing dangling scope: {} from: {}", danglingScope, current);
                    current.removeChildScope(danglingScope);
                }
                // returning null here should allow an implementer to create the child scope they wanted and not one of an unexpected type
                return null;
            }
            log.trace("Current: {} {}", current.getClass().getName(), current);
        }
        return current;
    }

}
