package fu.stockspace.stockspace_be.auth.service;

import fu.stockspace.stockspace_be.auth.dto.UpdateProfileRequest;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void updateProfile_updatesOnlyEditableProfileFields() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("tenant@example.com")
                .fullName("Old Name")
                .phone("0911111111")
                .avatarUrl("https://cdn.example.com/old.png")
                .build();
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("  New Name  ")
                .phone("0987654321")
                .avatarUrl("  https://cdn.example.com/new.png  ")
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        User result = profileService.updateProfile(userId, request);

        assertEquals("New Name", result.getFullName());
        assertEquals("0987654321", result.getPhone());
        assertEquals("https://cdn.example.com/new.png", result.getAvatarUrl());
        assertEquals("tenant@example.com", result.getEmail());
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_allowsClearingOptionalFields() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .fullName("Old Name")
                .phone("0911111111")
                .avatarUrl("https://cdn.example.com/old.png")
                .build();
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("New Name")
                .phone(null)
                .avatarUrl("  ")
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        User result = profileService.updateProfile(userId, request);

        assertNull(result.getPhone());
        assertNull(result.getAvatarUrl());
    }

    @Test
    void updateProfile_rejectsUnknownCurrentUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> profileService.updateProfile(
                userId,
                UpdateProfileRequest.builder().fullName("User").build()));
    }
}
