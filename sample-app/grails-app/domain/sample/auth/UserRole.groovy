package sample.auth

class UserRole {
    User user
    Role role

    static UserRole create(User user, Role role) {
        (UserRole) new UserRole(user: user, role: role).save(failOnError: true)
    }
}
