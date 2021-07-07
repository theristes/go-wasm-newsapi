package hotelliadmin

import grails.util.Environment
import Client.GatewayServiceClient
import DataContracts.CreditCardTransaction.CreditCard
import DataContracts.CreditCardTransaction.CreditCardTransaction
import DataContracts.CreditCardTransaction.CreditCardTransactionOptions
import DataContracts.Order.Order
import DataContracts.Sale.CreateSaleRequest
import grails.transaction.Transactional
import EnumTypes.PlatformEnvironmentEnum
import EnumTypes.CreditCardBrandEnum
import EnumTypes.CreditCardOperationEnum
import EnumTypes.CurrencyIsoEnum
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import java.net.*;

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

@Transactional
class LancamentoCartaoService {
    
    RestBuilder rest = new RestBuilder()   
    def grailsApplication

    def registrarLancamentoCartao(request, codigoDoServico, valor, empresaNaFatura, servico, tipo, cartao) {

		def lancamentoCartao = new LancamentoCartao(
			request:request,
			codigoServico:codigoDoServico,
			orderReference: montarCodigoReferenciaPedido(request.id, codigoDoServico),
			valor:valor,
			empresaFatura:empresaNaFatura,
			dataDaSolicitacao:request.dhRequest,
			servico:servico,
			numeroCartao: mascararNumeroCartao(cartao.numero),
			bandeiraCartao: cartao.bandeira,
			tipo: tipo
			)
		
		log.debug "lancamentoCartao.validate(): "+lancamentoCartao.validate();
		log.debug "lancamentoCartao.errors: "+lancamentoCartao.errors
		lancamentoCartao.save(flush:true)
    }


    def pagarNoGatewayJSONNovo(codigoPedido, cartao, valor){
        println "pagarNoGatewayJSONNovo"
        try {

            def accountId = '1C19A1B057E84E24012C192ECC326C0F';
            def prod = grails.util.Environment.current.name == "production"
            def apiKey
            def emailInfo
            if (!prod) {
            apiKey = 'db753aa76caa7182b67912af12da6240'
            emailInfo = 'ricardag@agsistemas.com.br'
            } else {
            apiKey = '693b69e82ed1898d9103cf3959b2148a'
            emailInfo = 'atendimento@hotelli.com.br'
            }

            def pieces = cartao.nome.split();

            def requestHead = [
                "account_id": accountId,
                "method": "credit_card",
                "test": !prod,
                
                "data": [
                    "number":  cartao.numero,
                    "verification_value": cartao.cvv,
                    "first_name": pieces.first(),
                    "last_name": pieces.last(),
                    "month": cartao.mesValidade.toString().padLeft(2,"0"),
                    "year": cartao.anoValidade.toString()
                ]
            ]

            def jsonRequestHead = new JsonBuilder( requestHead ).toPrettyString()

            RestResponse responseHead = rest.post("https://api.iugu.com/v1/payment_token"){
                header('Content-Type', 'application/json')
                header('Accept', 'application/json')
                    json jsonRequestHead
                }

            def retornoServico = responseHead.json
            def token = retornoServico.id

            def item = [
                "description": "Cobrança do pedido " + codigoPedido, 
                "quantity": 1,
                "price_cents": valor
            ]

            def request = [
            "token": token,
            "order_id": codigoPedido,
            "email": emailInfo,
            "items": [item]
            ]

            def jsonRequest = new JsonBuilder( request ).toPrettyString()

            RestResponse response = rest.post("https://api.iugu.com/v1/charge"){
                header('Content-Type', 'application/json')
                header('Accept', 'application/json')
                header('Authorization', 'Basic ' + apiKey.bytes.encodeBase64().toString())
                json jsonRequest
            }

            def retornoServicoItens = response.json

            def retorno = [
                sucesso: retornoServicoItens.success,
                erros: retornoServicoItens.errors,
                transacao: [
                    chave: retornoServicoItens.invoice_id,
                    codigoDeAutorizacao:retornoServicoItens.status,
                    resultado: retornoServicoItens
                ]
            ]
            log.debug "retorno: "+retorno
            return retorno 
        } catch(Exception e) {
            log.debug "metodo=pagarNoGatewayJSONNovo pedido=${codigoPedido} result=erro mensagem=${e.getMessage()}"
            
            return [
                sucesso: false,
                erros: e.getMessage(),
                transacao: [
                    chave: null,
                    resultado: null
                ]
            ]
        }
    }

    def estornarNoGatewayJSON(chaveDaCobranca){

        try {

        def retorno = [
                sucesso: true,
                erros: "",
                transacao: [
                    chave: "estorno-manual",
                    codigoDeAutorizacao:"estorno-manual",
                    resultado: null
                ]
            ]

        log.debug "retorno: "+retorno
            return retorno 
        } catch(Exception e) {
            log.debug "metodo=estornarNoGatewayJSON chaveDaCobranca=${chaveDaCobranca} result=erro mensagem=${e.getMessage()}"
            
            return [
                sucesso: false,
                erros: [e.getMessage()],
                transacao: [
                    chave: null,
                    resultado: null
                ]
            ]
        }
    }

    
    def montarCodigoReferenciaPedido(codigoPedido, codigoServico) {
        return "${codigoPedido}-${codigoServico}"
    }
    
    private CreditCardBrandEnum converterBandeiraCartao(bandeira) {
        def bandeiraCaixaBaixa = bandeira?.toLowerCase()
        
        def bandeiraMundipagg = CreditCardBrandEnum.values().find {
            it.toString()?.toLowerCase() == bandeiraCaixaBaixa
        }
        
        if(!bandeiraMundipagg) {
            throw new IllegalArgumentException("Bandeira inválida")
        }
        
        return bandeiraMundipagg
    }
    
    private mascararNumeroCartao(numero) {
        return numero?.reverse()?.take(4)?.reverse()?.padLeft(numero?.size(), "*")
    }

    def registrarPagamentoDoServico(params){
        if(params.prefixo == "TAX"){
            log.debug "PARAMS: "+params
            if(params.tipo == 'Taxa de Serviço'){
                def passageiroVoo = PassageiroVoo.get(params.idObjeto)
                log.debug "PASSAGEIRO VOO: "+passageiroVoo
                passageiroVoo.statusTaxaServico = "pago"
                passageiroVoo.save flush:true
            } else {
                def flightTaxes = FlightTaxes.get(params.idObjeto)
                flightTaxes.status = "pago"
                flightTaxes.save flush:true
            }
        } else if(params.prefixo == "OUT"){
            def outroServico = OutroServico.get(params.idObjeto)
            outroServico.status = "pago"
            outroServico.save flush:true
        } else if(params.prefixo == "HOT"){
            def hotel = Hotel.get(params.idObjeto)
            hotel.status = "pago"
            hotel.save flush:true
        } else if(params.prefixo == "SEG"){
            def seguroViagem = SeguroViagem.get(params.idObjeto)
            seguroViagem.status = "pago"
            seguroViagem.save flush:true
        } else if(params.prefixo == "TRF"){
            def transfer = Transfer.get(params.idObjeto)
            transfer.status = "pago"
            transfer.save flush:true
        } else if(params.prefixo == "ROD"){
            def passagemRodoviaria = PassagemRodoviaria.get(params.idObjeto)
            passagemRodoviaria.status = "pago"
            passagemRodoviaria.save flush:true
        } else if(params.prefixo == "ALU"){
            def aluguelCarro = AluguelCarro.get(params.idObjeto)
            aluguelCarro.status = "pago"
            aluguelCarro.save flush:true
        } else if(params.prefixo == "COB"){
            def cobrancasAluguelCarro = CobrancasAluguelCarro.get(params.idObjeto)
            cobrancasAluguelCarro.status = "pago"
            cobrancasAluguelCarro.save flush:true
        } else if(params.prefixo == "HOTTAX"){
            def hotelOtherServices = HotelOtherServices.get(params.idObjeto)
            hotelOtherServices.status = "pago"
            hotelOtherServices.save flush:true
        } else if(params.prefixo == "VOO"){
            def passageiroVoo = PassageiroVoo.get(params.idObjeto)
            passageiroVoo.status = "pago"
            passageiroVoo.save flush:true
        }
    }
}
