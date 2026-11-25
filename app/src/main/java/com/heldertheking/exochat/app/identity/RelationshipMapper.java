package com.heldertheking.exochat.app.identity;

import com.heldertheking.exochat.api.identity.AccountName;
import com.heldertheking.exochat.api.identity.RelationshipName;
import com.heldertheking.exochat.api.identity.UserName;
import com.heldertheking.exochat.app.identity.RelationshipRepository.RelationshipView;
import com.heldertheking.exochat.identity.v1alpha1.Relationship;

/// Converts relationships to protobuf messages.
final class RelationshipMapper {
    private RelationshipMapper() {}

    /// @param owner the account that owns the relationship, its name is the parent of the relationship name
    static Relationship relationship(RelationshipView view, AccountName owner) {
        var record = view.relationship();
        return Relationship.newBuilder()
                .setName(RelationshipName.of(owner, record.getId()).toString())
                .setTargetAccount(AccountName.of(UserName.of(view.targetUserId()), record.getTargetAccountId())
                        .toString())
                .setType(type(record.getType()))
                .setCreateTime(IdentityMapper.timestamp(record.getCreateTime()))
                .build();
    }

    private static Relationship.RelationshipType type(String type) {
        try {
            return Relationship.RelationshipType.valueOf("RELATIONSHIP_TYPE_" + type);
        } catch (IllegalArgumentException e) {
            return Relationship.RelationshipType.RELATIONSHIP_TYPE_UNSPECIFIED;
        }
    }
}
