package com.dha.identity.security;

import com.dha.identity.entity.User;
import com.dha.identity.entity.WebAuthnCredential;
import com.dha.identity.repository.UserRepository;
import com.dha.identity.repository.WebAuthnCredentialRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import lombok.RequiredArgsConstructor;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bridges the Yubico WebAuthn library's storage abstraction onto our User /
 * WebAuthnCredential JPA entities. The WebAuthn "username" is the user's
 * email (same identifier used for password login); the WebAuthn "user
 * handle" is the user's UUID packed into 16 bytes.
 */
@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class WebAuthnCredentialRepositoryAdapter implements CredentialRepository {

    private final UserRepository userRepository;
    private final WebAuthnCredentialRepository credentialRepository;

    public static ByteArray userHandleForId(UUID userId) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(userId.getMostSignificantBits());
        buffer.putLong(userId.getLeastSignificantBits());
        return new ByteArray(buffer.array());
    }

    public static UUID idFromUserHandle(ByteArray userHandle) {
        ByteBuffer buffer = ByteBuffer.wrap(userHandle.getBytes());
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return userRepository.findByEmail(username)
                .map(user -> credentialRepository.findByUserId(user.getId()).stream()
                        .map(this::descriptorOf)
                        .collect(Collectors.toSet()))
                .orElseGet(Set::of);
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepository.findByEmail(username).map(User::getId).map(WebAuthnCredentialRepositoryAdapter::userHandleForId);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return userRepository.findById(idFromUserHandle(userHandle)).map(User::getEmail);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return credentialRepository.findByCredentialId(credentialId.getBase64Url())
                .filter(cred -> cred.getUserId().equals(idFromUserHandle(userHandle)))
                .map(this::registeredCredentialOf);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return credentialRepository.findByCredentialId(credentialId.getBase64Url())
                .map(this::registeredCredentialOf)
                .map(Set::of)
                .orElseGet(Set::of);
    }

    private PublicKeyCredentialDescriptor descriptorOf(WebAuthnCredential cred) {
        try {
            return PublicKeyCredentialDescriptor.builder()
                    .id(ByteArray.fromBase64Url(cred.getCredentialId()))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt stored credential id", e);
        }
    }

    private RegisteredCredential registeredCredentialOf(WebAuthnCredential cred) {
        try {
            return RegisteredCredential.builder()
                    .credentialId(ByteArray.fromBase64Url(cred.getCredentialId()))
                    .userHandle(userHandleForId(cred.getUserId()))
                    .publicKeyCose(ByteArray.fromBase64Url(cred.getPublicKeyCose()))
                    .signatureCount(cred.getSignatureCount())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt stored credential", e);
        }
    }
}
