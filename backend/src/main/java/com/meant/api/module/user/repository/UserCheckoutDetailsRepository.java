package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserCheckoutDetails;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCheckoutDetailsRepository extends JpaRepository<UserCheckoutDetails, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO user_checkout_details (
                user_id, email, first_name, last_name, phone_number,
                street_address, extended_address, address_locality, address_region,
                postal_code, address_country, created_at, updated_at
            ) VALUES (
                :userId, :email, :firstName, :lastName, :phoneNumber,
                :streetAddress, :extendedAddress, :addressLocality, :addressRegion,
                :postalCode, :addressCountry, :now, :now
            )
            ON CONFLICT (user_id) DO UPDATE SET
                email = EXCLUDED.email,
                first_name = EXCLUDED.first_name,
                last_name = EXCLUDED.last_name,
                phone_number = EXCLUDED.phone_number,
                street_address = EXCLUDED.street_address,
                extended_address = EXCLUDED.extended_address,
                address_locality = EXCLUDED.address_locality,
                address_region = EXCLUDED.address_region,
                postal_code = EXCLUDED.postal_code,
                address_country = EXCLUDED.address_country,
                updated_at = CASE
                    WHEN ROW(
                        user_checkout_details.email,
                        user_checkout_details.first_name,
                        user_checkout_details.last_name,
                        user_checkout_details.phone_number,
                        user_checkout_details.street_address,
                        user_checkout_details.extended_address,
                        user_checkout_details.address_locality,
                        user_checkout_details.address_region,
                        user_checkout_details.postal_code,
                        user_checkout_details.address_country
                    ) IS DISTINCT FROM ROW(
                        EXCLUDED.email,
                        EXCLUDED.first_name,
                        EXCLUDED.last_name,
                        EXCLUDED.phone_number,
                        EXCLUDED.street_address,
                        EXCLUDED.extended_address,
                        EXCLUDED.address_locality,
                        EXCLUDED.address_region,
                        EXCLUDED.postal_code,
                        EXCLUDED.address_country
                    ) THEN EXCLUDED.updated_at
                    ELSE user_checkout_details.updated_at
                END
            """, nativeQuery = true)
    int upsert(
            @Param("userId") UUID userId,
            @Param("email") String email,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("phoneNumber") String phoneNumber,
            @Param("streetAddress") String streetAddress,
            @Param("extendedAddress") String extendedAddress,
            @Param("addressLocality") String addressLocality,
            @Param("addressRegion") String addressRegion,
            @Param("postalCode") String postalCode,
            @Param("addressCountry") String addressCountry,
            @Param("now") Instant now
    );
}
