package hotelliadmin

import grails.converters.JSON
import org.springframework.security.access.annotation.Secured
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import java.net.*;

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

import static org.springframework.http.HttpStatus.*
import grails.transaction.Transactional

@Secured(['ROLE_ADMIN', 'ROLE_NO_ROLES'])
class LancamentoCartaoController {
	RestBuilder rest = new RestBuilder()    
    def lancamentoCartaoService
	def requestChangeLogService
	def springSecurityService
    def requestService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond LancamentoCartao.list(params), model:[lancamentoCartaoInstanceCount: LancamentoCartao.count()]
    }

    def show(LancamentoCartao lancamentoCartaoInstance) {
        respond lancamentoCartaoInstance
    }

    def create() {
        respond new LancamentoCartao(params)
    }

    @Transactional
    def save(LancamentoCartao lancamentoCartaoInstance) {
        if (lancamentoCartaoInstance == null) {
            notFound()
            return
        }

        if (lancamentoCartaoInstance.hasErrors()) {
            respond lancamentoCartaoInstance.errors, view:'create'
            return
        }

        lancamentoCartaoInstance.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'lancamentoCartao.label', default: 'LancamentoCartao'), lancamentoCartaoInstance.id])
                redirect lancamentoCartaoInstance
            }
            '*' { respond lancamentoCartaoInstance, [status: CREATED] }
        }
    }

    def edit(LancamentoCartao lancamentoCartaoInstance) {
        respond lancamentoCartaoInstance
    }

    @Transactional
    def update(LancamentoCartao lancamentoCartaoInstance) {
        if (lancamentoCartaoInstance == null) {
            notFound()
            return
        }

        if (lancamentoCartaoInstance.hasErrors()) {
            respond lancamentoCartaoInstance.errors, view:'edit'
            return
        }

        lancamentoCartaoInstance.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'LancamentoCartao.label', default: 'LancamentoCartao'), lancamentoCartaoInstance.id])
                redirect lancamentoCartaoInstance
            }
            '*'{ respond lancamentoCartaoInstance, [status: OK] }
        }
    }

    @Transactional
    def delete(LancamentoCartao lancamentoCartaoInstance) {

        if (lancamentoCartaoInstance == null) {
            notFound()
            return
        }

        def requestInstance = lancamentoCartaoInstance.request

        lancamentoCartaoInstance.delete flush:true

        redirect(action: "show", id: requestInstance.id, controller: "request")
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'lancamentoCartao.label', default: 'LancamentoCartao'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
    
}
