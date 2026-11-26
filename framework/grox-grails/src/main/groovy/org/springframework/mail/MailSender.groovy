package org.springframework.mail

/** Compatibilidade com o {@code MailSender} do Spring (o Grox não oferece o bean; o envio é da aplicação). */
interface MailSender {
    void send(Object... messages)
}
