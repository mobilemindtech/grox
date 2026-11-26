package sample

/** modelAndView no after(), como nos interceptors do Grails. */
class MavInterceptor {
    MavInterceptor() {
        match(controller: 'home', action: 'mav')
    }

    boolean after() {
        if (modelAndView) modelAndView.model.extra = 'do interceptor'
        true
    }
}
