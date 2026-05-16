package com.synchat.backend.conversation.util;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import java.util.UUID;

public final class DirectConversationKeyUtil {

    private DirectConversationKeyUtil() {
    }

    public static String build(UUID userA, UUID userB) {
        if (userA == null || userB == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Peer user must be different from current user.");
        }

        if (userA.equals(userB)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Peer user must be different from current user.");
        }

        String first = userA.toString();
        String second = userB.toString();
        if (first.compareTo(second) > 0) {
            String temporary = first;
            first = second;
            second = temporary;
        }

        return "direct:" + first + ":" + second;
    }
}
