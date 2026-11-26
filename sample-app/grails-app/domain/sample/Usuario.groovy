package sample

class Usuario {
    String nome
    String email
    Integer idade
    Grupo grupo
    Date dateCreated
    byte[] foto
    String fotoTipo

    static constraints = {
        nome blank: false, size: 2..100
        email email: true, unique: true
        idade nullable: true, min: 0, max: 150
        grupo nullable: true
        foto nullable: true, maxSize: 2 * 1024 * 1024
        fotoTipo nullable: true, inList: ['image/png', 'image/jpeg', 'image/gif']
    }

    static mapping = {
        sort 'nome'
    }
}
