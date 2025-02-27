package com.insy2s.mskeycloak.service.impl;

import com.insy2s.mskeycloak.client.IMailClient;
import com.insy2s.mskeycloak.config.KeycloakConfig;
import com.insy2s.mskeycloak.dto.MailDto;
import com.insy2s.mskeycloak.error.exception.BadRequestException;
import com.insy2s.mskeycloak.error.exception.NotFoundException;
import com.insy2s.mskeycloak.model.Role;
import com.insy2s.mskeycloak.model.User;
import com.insy2s.mskeycloak.repository.IRoleRepository;
import com.insy2s.mskeycloak.repository.IUserRepository;
import com.insy2s.mskeycloak.utils.RandomUtils;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Service class for managing {@link User}.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService implements com.insy2s.mskeycloak.service.IUserService {

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final Keycloak keycloak;
    private final KeycloakConfig keycloakConfig;

    private final IMailClient mailClient;

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteUser(String id) {
        log.debug("SERVICE : deleteUser : {}", id);
        UsersResource usersResource = keycloak.realm(keycloakConfig.getRealm()).users();
        try {
            UserRepresentation user = usersResource.get(id).toRepresentation();

            try (Response response = usersResource.delete(id)) {
                if (user!=null && response.getStatus() != 204) {
                    throw new BadRequestException("Erreur lors de la suppression de l'utilisateur");
                }
            }
        }catch (Exception e){
            log.error("Utilisateur introuvable dans serveur Keycloak : {}", id);
        }
        userRepository.deleteById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toggleUserEnabled(String userId) {
        log.debug("SERVICE : toggleUserEnabled : {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
        UserRepresentation userRepresentation = keycloak
                .realm(keycloakConfig.getRealm())
                .users()
                .get(userId)
                .toRepresentation();
        final boolean previousStatus = userRepresentation.isEnabled();
        user.setEnabled(!previousStatus);
        userRepository.save(user);
        userRepresentation.setEnabled(!previousStatus);
        keycloak.realm(keycloakConfig.getRealm()).users().get(userId).update(userRepresentation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public User create(User user) {
        log.debug("SERVICE : createUser : {}", user);
        UserRepresentation newUser = new UserRepresentation();
        newUser.setUsername(user.getUsername());
        newUser.setEmail(user.getEmail());
        newUser.setFirstName(user.getFirstname());
        newUser.setLastName(user.getLastname());
        newUser.setEnabled(true);
        CredentialRepresentation credentials = new CredentialRepresentation();
        credentials.setTemporary(false);
        credentials.setType(CredentialRepresentation.PASSWORD);
        String password = RandomUtils.generateRandomString(30);
        user.setPassword(password);
        credentials.setValue(password);
        newUser.setCredentials(List.of(credentials));

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new BadRequestException("Email déjà existant");
        }

        Response response = keycloak.realm(keycloakConfig.getRealm()).users().create(newUser);
        if (response.getStatus() != 201) {
            throw new BadRequestException("Erreur lors de la création de l'utilisateur");
        }
        String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
        UserRepresentation createdUser = keycloak
                .realm(keycloakConfig.getRealm())
                .users()
                .get(userId)
                .toRepresentation();

        Collection<Role> roles = user.getRoles();
        // Assign the desired role to the user
        if (!roles.isEmpty()) {

            for (Role r : roles) {
                RoleRepresentation roleRepresentation = keycloak
                        .realm(keycloakConfig.getRealm())
                        .roles()
                        .get(r.getName())
                        .toRepresentation();
                log.info("SERVICE : roleRepresentation : {}" , roleRepresentation);
                keycloak.realm(keycloakConfig.getRealm()).users()
                        .get(userId)
                        .roles()
                        .realmLevel()
                        .add(Collections.singletonList(roleRepresentation));
            }
        }


        User userToCreateInLocalDb = User.builder()
                .username(user.getUsername())
                .firstname(user.getFirstname())
                .lastname(user.getLastname())
                .id(createdUser.getId())
                .email(user.getEmail())
                .roles(roles)
                .dateInscription(new Date())
                .password(password)
                .build();
        userToCreateInLocalDb=userRepository.save(userToCreateInLocalDb);
       // String fullname, String mailTo, String subject, String username, String password, String body)
        MailDto mail=new MailDto("creationAccount",user.getFirstname(),user.getEmail(),"Création du compte",user.getUsername(),password,"Création du compte");
      mail.setUsername(user.getUsername());
      mail.setPassword(password);
       mail.setMailTo(user.getEmail());
        System.out.println("mail ds qu"+mail.getMailTo());
        mailClient.sendAddEmail(mail);
        return userToCreateInLocalDb;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        log.debug("SERVICE : getUser : {}", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public User findById(String id) {
        log.debug("SERVICE : getUserById : {}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        log.debug("SERVICE : getUsers");
        return userRepository.findAll();
    }

    @Override
    public User updateUser(User user) {
        final String userId = user.getId();
        UserResource existingUserResource = keycloak.realm(keycloakConfig.getRealm()).users().get(userId);
        UserRepresentation existingUser = existingUserResource.toRepresentation();
        if (existingUser == null) {
            throw new NotFoundException("Utilisateur non trouvé");
        }
        existingUser.setFirstName(user.getFirstname());
        existingUser.setLastName(user.getLastname());
        existingUser.setEmail(user.getEmail());
        existingUserResource.update(existingUser);
        if(CollectionUtils.isEmpty(user.getRoles())){
            return userRepository.save(user);
        }
        List<RoleRepresentation> existingUserRoles = existingUserResource
                .roles()
                .realmLevel()
                .listAll();
        existingUserResource
                .roles()
                .realmLevel()
                .remove(existingUserRoles);

        List<RoleRepresentation> rolesToAdd = user.getRoles().stream()
                .map(role -> keycloak
                        .realm(keycloakConfig.getRealm())
                        .roles()
                        .get(role.getName())
                        .toRepresentation())
                .toList();

        existingUserResource
                .roles()
                .realmLevel()
                .add(rolesToAdd);

        return userRepository.save(user);
    }

}
