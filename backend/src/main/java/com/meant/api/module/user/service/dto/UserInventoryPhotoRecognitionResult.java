package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserInventoryCategory;
import java.util.List;

public record UserInventoryPhotoRecognitionResult(
        String name,
        String brand,
        UserInventoryCategory category,
        String description,
        List<String> attributes,
        Boolean consumable
) {
}
