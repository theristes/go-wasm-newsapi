package hotelliadmin

import grails.transaction.Transactional
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper


import org.codehaus.groovy.grails.web.context.ServletContextHolder as SCH
@Transactional
class RequestService {
	
	final FORNECEDOR_MILHAS = "Milhas Fácil"
	def pdfRenderingService
	def servletContext = SCH.servletContext
	def grailsApplication
  def lancamentoCartaoService
	def requestChangeLogService
	def springSecurityService

	def recuperarVoucherDoHotel(request) {
		return Voucher.findByCodSeguranca(request?.hotel?.getPersistentValue('reservationCode'))
	}
    
    def desassociarVoo(request, voo) {
        request.removeFromFlights(voo)
        voo.delete()
    }
    
    def removerListaVoos(request, voosExclusao) {
        for(voo in voosExclusao) {
            desassociarVoo(request, voo)
        }
    }
    
    def removerHotel(request) {
        def hotel = request.hotel        
        if (hotel) {
            request.hotel = null
            hotel.delete()
        }
        
        def hotelRequestData = request.hotelRequestData        
        if (hotelRequestData) {
            request.hotelRequestData = null
            hotelRequestData.delete()
        }
    }

	def salvarPassageiroVoo(params){
		for(def ip = 0; params["passageiroVoos[$ip]"]; ip++){
			def passageiroVoo = null
			if(params["passageiroVoos[$ip].id"]){
				passageiroVoo = PassageiroVoo.get(params["passageiroVoos[$ip].id"])
				passageiroVoo.seat = params["passageiroVoos[$ip].seat"]
				passageiroVoo.ticketNumber = params["passageiroVoos[$ip].ticketNumber"]
				passageiroVoo.localizer  = params["passageiroVoos[$ip].localizer"]
				passageiroVoo.taxes = params.list(["passageiroVoos[$ip].taxes"])
			} else {
				passageiroVoo = new PassageiroVoo(params["passageiroVoos[$ip]"])
			}
			passageiroVoo.save()
		}
	}
	
	def recuperarLogs(requestInstance){
		return RequestChangeLog.findAll {
			objectId == requestInstance.id
			object == 'request'
		}
	}
	
	def recuperarPaymentLogs(requestInstance){
		return RequestChangeLog.findAll {
			objectId == requestInstance.id
			object == 'payment'
		}
	}
	
	def cancelarTaxasAereas(requestInstance){
		if(requestInstance?.flights){
			requestInstance?.flights.each {
				for(x in it.passageirosVoo){
					x.statusTaxaServico = 'cancelado'
				}
			}
		}
	}
	
	def verificaDadosDeVooParaEmitir(requestInstance){
		if(requestInstance.temVoo()){
			for (voo in requestInstance?.flights) {
				for (passageiroVoo in voo?.passageirosVoo) {
					if(!passageiroVoo.ticketNumber && !passageiroVoo.localizer){
						return false
					}
				}
			 }
			
			requestInstance.tripRequest.status = Status.get(4)
			requestInstance.tripRequest.save(flush:true)
			
			return true
		}
		return true

	}
	
	def verificaDadosDeHotelParaEmitir(requestInstance){
		//Salva o Pedido

		if(requestInstance.temHotel()){
			if(requestInstance.hotel?.reservationCode != null){
				def noites = 0
				
				use(groovy.time.TimeCategory) {
					def duration = requestInstance.hotel.checkout - requestInstance.hotel.checkin
					noites = duration.days
					
					// if(duration.hours >= 23){
					// 	noites += 1
					// }

					// if(duration.hours <= 1 && duration.hours != 0){
					// 	noites -= 1
					// }
				}
				
				if(noites == 0)
					noites = 1
	
				def hospedesArray = []
				requestInstance.passageiros.each {
					hospedesArray.add(it.name + " " + it.surname)
				}

				def pedido = new Pedido(
						funcionario: requestInstance.autor,
						empresa: requestInstance.empresa,
						qtd:1, //TODO Verificar porque está fixo o valor 1
						valorUnitario: requestInstance.tripRequest.hotelCost / noites / requestInstance.numRooms,
						oferta: requestInstance.hotel.quarto,
						aceitaPagamentoFaturadoCheckout: requestInstance.tripRequest.billingCheckout,
						hospedes: hospedesArray.join(";"),
						dtReserva: requestInstance.hotel.checkin,
						diarias: noites,
						quartos: requestInstance.numRooms,
						taxa: requestInstance.tripRequest.taxes,
						faturamentoRazaoSocial: requestInstance.empresa.nome,
						faturamentoCnpj: requestInstance.empresa.cnpj,
						faturamentoEndereco: requestInstance.empresa.getEnderecoCompleto(),
						faturamentoCidade: requestInstance.empresa.cidade,
						faturamentoEstado: requestInstance.empresa.estado,
						)
                pedido.validate()
				log.debug "request=${requestInstance.id} metodo=verificaDadosDeHotelParaEmitir acao=exibindo_dados_request dados=${requestInstance?.properties}"
				log.debug "request=${requestInstance.id} metodo=verificaDadosDeHotelParaEmitir acao=exibindo_dados_trip_request dados=${requestInstance?.tripRequest?.properties}"
				log.debug "request=${requestInstance.id} metodo=verificaDadosDeHotelParaEmitir acao=exibindo_dados_hotel dados=${requestInstance?.hotel?.properties}"
				log.debug "request=${requestInstance.id} metodo=verificaDadosDeHotelParaEmitir acao=exibindo_dados_hospedes dados=${hospedesArray?.join(";")}"
				log.debug "request=${requestInstance.id} metodo=verificaDadosDeHotelParaEmitir acao=validacao_pedido errors=${pedido?.errors}"

                
				pedido.save(flush:true)
	
	
				def idStatusVoucher = 1 //Aguardando Pagamento
				if(requestInstance.tripRequest.paymentType == 'invoice'){
					idStatusVoucher = 3 //Pago
				}
	
				log.debug "requestInstance.hotel.reservationCode: "+requestInstance.hotel.reservationCode
				def voucher = new Voucher(
						funcionario: requestInstance.autor,
						quarto: requestInstance.hotel.quarto,
						idStatusVoucher: idStatusVoucher,
						valor: requestInstance.tripRequest.hotelCost,
						pedido: pedido,
						dtReserva: requestInstance.hotel.checkin,
						numDiarias: noites,
						quartos: requestInstance.numRooms,
						taxa: requestInstance.tripRequest.taxes,
						codSeguranca: requestInstance.hotel.reservationCode,
						)
	
				voucher.save(flush: true)
				requestInstance.tripRequest.status = Status.get(4)
				requestInstance.tripRequest.save(flush:true)
				
				return true
			} else {
				return false
			}
		}
		return true
	}

	def emitir(requestInstance){
		if( verificaDadosDeHotelParaEmitir(requestInstance) && verificaDadosDeVooParaEmitir(requestInstance) ){
			requestInstance.tripRequest.status = Status.get(8)
			requestInstance.origemEmissao = 'admin'
			requestInstance.tripRequest.save(flush:true)
			
			new RequestLog(
				operador: springSecurityService?.principal?.username,
				acao : 'Emissão',
				dhAcao: new Date(),
				request: requestInstance	
			).save()
		}
	}
	
	def gerarVoucherEstabelecimento(requestInstance){
		def vouchers = []
		def voucher = recuperarVoucherDoHotel(requestInstance)
		def quarto = requestInstance?.hotel?.quarto
		def estabelecimento = quarto?.estabelecimento
		def pedido = voucher.pedido
		def arquivoVoucher = grailsApplication.config.caminhoVouchers + 'voucherEstabelecimento' + requestInstance.id + '.pdf'
		
		pdfRenderingService.render(template: "/voucher/voucherEstabelecimento", model:['requestInstance': requestInstance, 'estabelecimento': estabelecimento, 'quarto': quarto, 'voucher': voucher, 'pedido': pedido], new File(arquivoVoucher).newOutputStream())
		vouchers.add(new File(arquivoVoucher))
		
		return vouchers

	}

	def gerarVouchers(requestInstance){
		def vouchers = []
		def voucher = false

		def logo = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets + requestInstance.empresa.agenciaTenant.mapConfiguracoes['nomeArquivoLogoVoucher']))
		if(requestInstance.temHotel){
			voucher = this.recuperarVoucherDoHotel(requestInstance)
			if(voucher){
				def arquivoVoucher = grailsApplication.config.caminhoVouchers + 'voucherHotel' + requestInstance.id + '.pdf'

				log.debug "voucher.codSeguranca: "+voucher.codSeguranca

				//def codigoDeBarras = (grailsApplication.config.urlBaseSite + "/drawbarcode?code=$voucher.codSeguranca").toURL().getBytes()
				pdfRenderingService.render(template: "/voucher/hotel", model:['requestInstance': requestInstance, 'voucher':voucher, 'logo':logo.bytes], new File(arquivoVoucher).newOutputStream())
				vouchers.add(new File(arquivoVoucher))
			}
		}

		if(requestInstance.temVoo && requestInstance.tripRequest.status.id == 8){ //TODO 8 É emitido. Criar ENUM
			def logosCompanhiasAereas = [:]
			def valorTotalMulta = 0

			requestInstance.flights.each {
				def companhia =  it.company
				def logoCompanhiaAerea = getLogoCompanhiasAereas(it)
				def multas = it.passageirosVoo.taxes.flatten().findAll() { def taxas ->  taxas.type == "MCOMULTA" }

				if(multas){
					valorTotalMulta += multas.price.sum()
				}

				try {
					if(logoCompanhiaAerea) {
						logoCompanhiaAerea = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets +logoCompanhiaAerea))
					}
					logosCompanhiasAereas.put(companhia, logoCompanhiaAerea.bytes)
				} catch(e){
					logosCompanhiasAereas.put(companhia, null)
				}
			}

			def arquivoVoucher = grailsApplication.config.caminhoVouchers  + 'voucherVoos' + requestInstance.id + '.pdf'
			pdfRenderingService.render(template: "/voucher/voo", model:['requestInstance': requestInstance, 'logo':logo.bytes, 'logosCompanhiasAereas':logosCompanhiasAereas], new File(arquivoVoucher).newOutputStream(), 'valorTotalMulta':valorTotalMulta)
			vouchers.add(new File(arquivoVoucher))
		}

		return vouchers
	}
	
	def gerarVoucherIndividual(requestInstance, passageiroInstance){
		def vouchers = []
		def voucher = false

		def logo = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets + requestInstance.empresa.agenciaTenant.mapConfiguracoes['nomeArquivoLogoVoucher']))
		
		if(requestInstance.temHotel){
			voucher = this.recuperarVoucherDoHotel(requestInstance)
			if(voucher){
				def arquivoVoucher = grailsApplication.config.caminhoVouchers + 'voucherHotel' + requestInstance.id + '.pdf'

				log.debug "voucher.codSeguranca: "+voucher.codSeguranca

				//log.debug (grailsApplication.config.urlBaseSite+"/drawbarcode?code=$voucher.codSeguranca")

				//def codigoDeBarras = (grailsApplication.config.urlBaseSite + "/drawbarcode?code=$voucher.codSeguranca").toURL().getBytes()
				pdfRenderingService.render(template: "/voucher/hotelIndividual", model:['requestInstance': requestInstance, 'passageiroInstance': passageiroInstance, 'voucher':voucher, 'logo':logo.bytes], new File(arquivoVoucher).newOutputStream())
				vouchers.add(new File(arquivoVoucher))
			}
		}

		if(requestInstance.temVoo && requestInstance.tripRequest.status.id == 8){ //TODO 8 É emitido. Criar ENUM
			def logosCompanhiasAereas = [:]
			def valorTotalMulta = 0

			requestInstance.flights.each {
				def companhia =  it.company
				def logoCompanhiaAerea = getLogoCompanhiasAereas(it)
				def multas = [] 

				if(it.passageirosVoo?.taxes){
					it.passageirosVoo?.taxes?.flatten().findAll() { def taxas ->  taxas?.type == "MCOMULTA" }

				}

				if(multas){
					valorTotalMulta += multas?.price?.sum()
				}

				try {
					log.debug "logoCompanhiaAerea: "+logoCompanhiaAerea
					log.debug "grailsApplication.config.caminhoAssets: "+grailsApplication.config.caminhoAssets
					//log.debug "file: "+new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets +logoCompanhiaAerea))
					if(logoCompanhiaAerea) {
						logoCompanhiaAerea = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets +logoCompanhiaAerea))
					}
					logosCompanhiasAereas.put(companhia, logoCompanhiaAerea.bytes)
				} catch(e){
					log.debug "Exception: "+e
					logosCompanhiasAereas.put(companhia, null)
				}
			}

			log.debug "requestInstance.passageiros.size(): "+requestInstance.passageiros.size()
			log.debug "requestInstance.passageiros: "+requestInstance.passageiros
			log.debug "requestInstance.passageiros.id.unique().size(): "+requestInstance.passageiros.id.unique().size()



			def arquivoVoucher = grailsApplication.config.caminhoVouchers  + 'voucherVoos' + requestInstance.id + '.pdf'
			pdfRenderingService.render(template: "/voucher/vooIndividual", model:['requestInstance': requestInstance, 'passageiroInstance':passageiroInstance, 'logo':logo.bytes, 'logosCompanhiasAereas':logosCompanhiasAereas, 'valorTotalMulta':valorTotalMulta], new File(arquivoVoucher).newOutputStream())
			vouchers.add(new File(arquivoVoucher))
		}

		return vouchers
	}

	def getLogoCompanhiasAereas(voo, ehChamadaInterna = true){
		def companhiaNome = voo?.company
		def companhia = Airlines.findAllByName(companhiaNome)
		def logo = ""
		
		if(ehChamadaInterna){
			logo = "companhias-aereas/"
		} else {
		 	logo = grailsApplication.config.caminhoImagensDoSite + "flight-companies/"
		}
		
		if(companhia && companhia.size() > 0){
			return logo + 'email-'+companhia.first().iata+'.png'
		}

	}

	def validarEmissao(requestInstance){ //TODO EXtrair mensagens
		def valido = true

		// if(requestInstance.tripRequest.status == 8){
		// 	log.debug "INVALIDO 111111111"
		// 	return false
		// }


		if(requestInstance.temHotel && !requestInstance.hotel.reservationCode){
			log.debug "Necessário informar o código da reserva para emitir o pedido"
			return "Necessário informar o código da reserva para emitir o pedido"
		}
		
		// if(requestInstance.temVoo && (requestInstance.flights.hasErrors())){
		// 	log.debug "Inválido devido à erros em flights ou passageirosVoo"
		// 	return requestInstance.flights.passageirosVoo.errors
		// }

		if(requestInstance.temHotel && requestInstance.hotel?.hasErrors()){
			return requestInstance.hotel.errors
		}


		if(requestInstance.tripRequest.hasErrors()){
			log.debug "Inválido devido à erros no hotel ou tripRequest"
			return requestInstance.tripRequest.errors
			return false
		}

		//Valida assentos e localizadores
		if(requestInstance?.temVoo){
			requestInstance?.flights.each { def flight ->
				flight?.passageirosVoo?.each { def  passageiroVoo  ->
					if(passageiroVoo?.seat == null || (passageiroVoo?.localizer == null && passageiroVoo?.ticketNumber == null)){ 
						log.debug "Inválido devido à erros relacionados ao localizador"
						return "Para emitir é necessário informar o assento e localizador ou ticket"
						log.debug "Inválido devido à erros relacionados ao localizador"
						return "Para emitir é necessário informar o assento e localizador ou ticket"
						valido = false 
						} 
					}
			}
		}

		return valido
	}
    
    def obterCartaoCreditoRequest(Request request) {
        def cartao
        
        TripRequest tripRequest = request.tripRequest
        
        if (!(tripRequest?.paymentType in ['my-card', 'company-card'])) {
            throw new IllegalAccessException("O pedido informado não possui forma de pagamento válida")
        }
        
        if (tripRequest.paymentType == 'company-card') {
            if (request.temVoo() && !tripRequest.idcartaoCreditoFlight) {
                throw new IllegalStateException("Não existe cartão da empresa para pagar voo associado ao pedido")
            }

            if(request.temHotel() && !tripRequest.idcartaoCreditoHotel) {
                throw new IllegalStateException("Não existe cartão da empresa para pagar hotel associado ao pedido")
            }

            if(!(tripRequest.idcartaoCredito || tripRequest.idcartaoCreditoFlight || tripRequest.idcartaoCreditoHotel)) {
                throw new IllegalStateException("Não existe cartão da empresa associado ao pedido")
            }
            
            EmpCartaoCredito empCartaoCredito
            
            if (request.temVoo()) {
                empCartaoCredito = EmpCartaoCredito.get(tripRequest.idcartaoCreditoFlight)
            } else if(request.temHotel()) {
                empCartaoCredito = EmpCartaoCredito.get(tripRequest.idcartaoCreditoHotel)
            } else {
				empCartaoCredito = EmpCartaoCredito.get(tripRequest.idcartaoCredito)
            }
            
            cartao = [
                nome: empCartaoCredito?.nome,
                bandeira: empCartaoCredito?.bandeira,
                numero: AesCryptor.decode(empCartaoCredito?.numeroCartao),
                mesValidade: empCartaoCredito?.validadeMes,
                anoValidade: empCartaoCredito?.validadeAno,
                cvv: empCartaoCredito.cvv
            ]
        } else {
            cartao = [
                nome: tripRequest?.cardName,
                bandeira: tripRequest?.cardBrand,
                numero: tripRequest?.cardNumber,
                mesValidade: Integer.parseInt(tripRequest?.cardExpirationMonth),
                anoValidade: Integer.parseInt(tripRequest?.cardExpirationYear),
                cvv: tripRequest?.cardCode
            ]
        }
        
        return cartao
    }
	
	def recuperarValorTotalPorLocalizador(requestInstance){
		def cobraValorTotal = false
		
		if(requestInstance?.empresa?.cobraDu && requestInstance?.originCountry?.toUpperCase() == "BR" && requestInstance?.destinationCountry?.toUpperCase() == "BR"){
			cobraValorTotal = true
		}
		
		def voosAgrupados = [:]
		 

		if(requestInstance.flights){
			def voosIda = requestInstance.getVoosIda().sort{it.departureTime}
			def voosVolta = requestInstance.getVoosVolta().sort{it.departureTime}
			def tipoViagem = "RT"
			def idaEVolta = requestInstance.flights.tipo.unique().size() == 2
			
			if(requestInstance.tripRequest.tipoTarifaVoo == "OW"){
				tipoViagem = "OW"
			}
			
			if("RT" == tipoViagem){	
				def localizadores = requestInstance.flights.passageirosVoo.localizer.flatten().unique()
				def totalDosVoos =  new BigDecimal(requestInstance.flights.price.sum())
				def totalTaxas = new BigDecimal(requestInstance.flights.serviceTaxes.sum())
				if(localizadores){
					if(!cobraValorTotal){
						totalDosVoos = new BigDecimal(totalDosVoos - totalTaxas)
					}
					
					def totalPorVoo = Math.round(new BigDecimal(totalDosVoos / localizadores.size()) * 100.0) / 100.0
					def companhia = voosIda.first().company
					def parceiro = voosIda.first().channel.name
					//def loc = voosIda.first().localizer
					
					localizadores.each {
						voosAgrupados.put(it, ['valor':totalPorVoo, 'cia':companhia, 'parceiro':parceiro, 'loc':it])
						}
				}
			} else {
				def localizadoresIda = voosIda.passageirosVoo.localizer.flatten().unique()
				if(localizadoresIda){
					
					def totalDosVoos =  new BigDecimal(voosIda.price.sum())
					def totalTaxas = new BigDecimal(voosIda.serviceTaxes.sum())
					if(!cobraValorTotal){
						totalDosVoos = new BigDecimal(totalDosVoos - totalTaxas) 
					}
					
					def totalPorVoo = Math.round(new BigDecimal(totalDosVoos / localizadoresIda.size()) * 100.0) / 100.0
					def companhia = voosIda.first().company
					def parceiro = voosIda.first().channel.name
					//def loc = voosIda.first().localizer
					
					localizadoresIda.each {
						voosAgrupados.put(it, ['valor':totalPorVoo, 'cia':companhia, 'parceiro':parceiro, 'loc': it])
					}
				}
				
				if(idaEVolta){
					def localizadoresVolta = voosVolta.passageirosVoo.localizer.flatten().unique()
					if(localizadoresVolta){
						
						def totalDosVoosVolta =  new BigDecimal(voosVolta.price.sum())
						def totalTaxasVolta = new BigDecimal(voosVolta.serviceTaxes.sum())
						
						if(!cobraValorTotal){
							totalDosVoosVolta = new BigDecimal(totalDosVoosVolta - totalTaxasVolta) 
						}
						
						def totalPorVooVolta = Math.round(new BigDecimal(totalDosVoosVolta / localizadoresVolta.size()) * 100.0) / 100.0
						def companhiaVooVolta = voosVolta.first().company
						def parceiroVooVolta = voosVolta.first().channel.name
						//def locVolta = voosVolta.first().localizer
						localizadoresVolta.each {
							voosAgrupados.put(it, ['valor':totalPorVooVolta, 'cia':companhiaVooVolta, 'parceiro':parceiroVooVolta, 'loc': it])
						}
					}
				}
			}
		}
		
		return voosAgrupados
	}
	
	def registrarPagamentoDoAereo(requestInstance){
		def voosAgrupados = requestInstance.flights ? recuperarValorTotalPorLocalizador(requestInstance) : [:]
		def cartaoHotelli = getCartaoHotelli(requestInstance)
		def cartaoPedido = obterCartaoCreditoRequest(requestInstance)

		if(cartaoHotelli){
			cartaoHotelli = [
                nome: cartaoHotelli?.nome,
                bandeira: cartaoHotelli?.bandeira,
                numero: AesCryptor.decode(cartaoHotelli?.numeroCartao),
                mesValidade: cartaoHotelli?.validadeMes,
                anoValidade: cartaoHotelli?.validadeAno,
                cvv: cartaoHotelli.cvv
            ]
		}

		voosAgrupados.values().each { def voo ->
			if(voo.parceiro != FORNECEDOR_MILHAS || cartaoHotelli){
				def gateway = voo.cia
				if(voo.cia.toLowerCase().contains('Gol Linhas'.toLowerCase()) && voo.parceiro.toLowerCase().contains('Rextur'.toLowerCase())){
					gateway = 'READ*GOL ['+voo.loc+']';
				}
				lancamentoCartaoService.registrarLancamentoCartao(
					requestInstance,
					'VOO',
					voo.valor,
					gateway,
					'Aéreo',
					'Passagem',
					cartaoHotelli ? cartaoHotelli : cartaoPedido
				)
				

				def argumentosParaLog = ['Aéreo', 'Voo', voo.valor, voo.parceiro, gateway]
				argumentosParaLog.add(' - ') //Código de autorização
				argumentosParaLog.add(' - ') //Erro
				requestChangeLogService.registrarPagamento(requestInstance,argumentosParaLog,"sucesso")
			}
		}
	}
	

	def registrarPagamentoDeHotel(requestInstance, cartaoHotelli = null){

		def valor = (requestInstance?.tripRequest?.hotelCost + requestInstance?.tripRequest?.taxes)
		
		def origin = requestInstance?.hotelRequestData?.origin

		def cartaoPedido = obterCartaoCreditoRequest(requestInstance)

		if(cartaoHotelli){
			cartaoHotelli = [
                nome: cartaoHotelli?.nome,
                bandeira: cartaoHotelli?.bandeira,
                numero: AesCryptor.decode(cartaoHotelli?.numeroCartao),
                mesValidade: cartaoHotelli?.validadeMes,
                anoValidade: cartaoHotelli?.validadeAno,
                cvv: cartaoHotelli.cvv
            ]

            valor = new BigDecimal(requestInstance?.tripRequest?.valorHotel + requestInstance?.tripRequest?.valorTaxaHotel)
		}

		lancamentoCartaoService.registrarLancamentoCartao(
			requestInstance,
			'HOT' + requestInstance.hotel.id,
			valor,
			origin, //Empresa na Fatura
			'Hotelaria',
			'Diária',
			cartaoHotelli ? cartaoHotelli : cartaoPedido
		)
		
		def argumentosParaLog = ['Hotelaria', 'Diária', valor, origin, origin]
		argumentosParaLog.add(' - ') //Código de autorização
		argumentosParaLog.add(' - ') //Erro
		requestChangeLogService.registrarPagamento(requestInstance,argumentosParaLog,"sucesso")
		
		requestInstance?.hotel?.status = "pago"
		requestInstance?.hotel.save flush:true
	}
	
	def registrarComissaoDeHotel(requestInstance){
		def valorHotel = requestInstance.tripRequest.hotelCost + requestInstance.tripRequest.taxes
		def origin = requestInstance.hotel.quarto.estabelecimento.origin.name
		
		def tipoComissao = "valorReal"
		def valorComissao = 0
		
		if("hotelli" == origin?.toLowerCase()){
			tipoComissao = "porcentagem"
			valorComissao = requestInstance.hotel.quarto.comissaoFanbiz
		} else if(requestInstance.hotel.quarto.estabelecimento.origin.percentualComissao){
			tipoComissao = "porcentagem"
			valorComissao = requestInstance.hotel.quarto.estabelecimento.origin.percentualComissao
		}
		
		requestInstance.hotel.tipoComissao = tipoComissao
		requestInstance.hotel.valorComissao = valorComissao
		requestInstance.hotel.save flush:true
	}
	
	def registrarComissaoDeLocacao(requestInstance){
		requestInstance.aluguelCarros.each {
			it.valorComissao = it.canal.percentualComissao
			it.save flush:true	
		}
	}
	
	def registrarComissaoDeSeguroDeViagem(requestInstance){
		requestInstance.segurosViagem.each {
			it.valorComissao = it.seguradora.percentualComissao
			it.save flush:true
		}
	}

	def validarOrcamentoDeCadaCentroDeCusto(requestInstance, valorACobrar, justificativa){
		if(requestInstance?.empresa?.controlaOrcamento && requestInstance?.tripRequest?.paymentType != 'my-card'){
			def valorRateadoPorCentroDeCusto = ratear(requestInstance, valorACobrar)
			def centroDeCustoPorResultado = ['bloqueia':[:], 'alerta': [:], 'aprovado':[:]]
			log.debug "valorRateadoPorCentroDeCusto: "+valorRateadoPorCentroDeCusto

			valorRateadoPorCentroDeCusto.each {
				def retornoValidacao = validarOrcamentoTotal(requestInstance, it.value, it.key)
				
				log.debug "value: "+it.value
				log.debug "key: "+it.key
				log.debug "retornoValidacao: "+retornoValidacao
	
				if("bloqueia" == retornoValidacao){
					registrarLogDeOrcamento(requestInstance, "Bloqueio", retornoValidacao.tipoValidacao, it.value, null, 'Compra bloqueada', it.key)
				}
	
				centroDeCustoPorResultado[retornoValidacao.resultado].put(it.key, retornoValidacao.tipoValidacao)
			}
	
			if(centroDeCustoPorResultado['bloqueia'].size() > 0){
				return "orcamento-bloqueia"
			}
	
			if(centroDeCustoPorResultado['bloqueia'].size() == 0 && centroDeCustoPorResultado['alerta'].size() > 0){
				if(justificativa){
					centroDeCustoPorResultado['alerta'].each {
						registrarLogDeOrcamento(requestInstance, "Alerta", it.value, valorRateadoPorCentroDeCusto[it.key], justificativa, 'Compra realizada com justificativa', it.key)
					}
	
					return "aprovado"
				}
	
				return "orcamento-alerta"
			}
	
			return "aprovado"
		}
	}

	def validarOrcamentoTotal(requestInstance, valorAConsumir, centroDeCusto){
		log.debug "valorAConsumir: "+valorAConsumir

		log.debug "cCENTRO DE CUSTO"
		log.debug centroDeCusto = EmpCentroCusto.get(centroDeCusto)

		if(!requestInstance?.empresa?.controlaOrcamento){
			return ["resultado":"aprovado"]
		}

		if(!centroDeCusto){
			return ["resultado": "bloqueia", "tipoValidacao":"anual"]
		}

		def mesCorrente = new Integer(new Date().format("MM"));
		def anoCorrente = new Integer(new Date().format("yyyy"));

		//Verificar se precisa usar dhRequest

		def consumoOrcamentoSintetico = ConsumoOrcamentoSintetico.withCriteria {
			eq('ano', anoCorrente)
			eq('centroDeCusto', centroDeCusto)
		}

		def valorConsumidoAnual = consumoOrcamentoSintetico.valorConsumido.sum()
		if(!valorConsumidoAnual){
			valorConsumidoAnual = 0
		}

		def orcamentoAnual = BudgetEmpresaAnual.withCriteria(uniqueResult: true) {
			eq('ano', anoCorrente)
			eq('centroDeCusto', centroDeCusto)
		}

		orcamentoAnual = orcamentoAnual

		def alertaOuBloqueiaAnual = verificarSeAlertaOuBloqueia(orcamentoAnual, valorConsumidoAnual, valorAConsumir)

		log.debug "Validação de orçamento anual: " + alertaOuBloqueiaAnual

		if("Bloqueia" == alertaOuBloqueiaAnual){
			return ["resultado": "bloqueia", "tipoValidacao":"anual"]
		}

		def valorConsumidoMensal = consumoOrcamentoSintetico.find() { it.mes == mesCorrente }
		if(!valorConsumidoMensal){
			valorConsumidoMensal = 0
		} else {
			valorConsumidoMensal = valorConsumidoMensal.valorConsumido
		}

		def orcamentoMensal = BudgetEmpresaMensal.withCriteria(uniqueResult: true) {
			eq('ano', anoCorrente)
			eq('mes', mesCorrente)
			eq('centroDeCusto', centroDeCusto)
		}

		def alertaOuBloqueiaMensal = verificarSeAlertaOuBloqueia(orcamentoMensal, valorConsumidoMensal, valorAConsumir)

		log.debug "Validação de orçamento mensal: " + alertaOuBloqueiaMensal
		

		if("Bloqueia" == alertaOuBloqueiaMensal){
			return ["resultado": "bloqueia", "tipoValidacao":"mensal"]
		}

		if("aprovado" == alertaOuBloqueiaMensal && "aprovado" == alertaOuBloqueiaAnual){
			return ["resultado":"aprovado"]
		}

		if("Alerta" == alertaOuBloqueiaAnual){
			return ["resultado": "alerta", "tipoValidacao":"anual"]
		} 

		return ["resultado": "alerta", "tipoValidacao":"mensal"]
	}

	def verificarSeAlertaOuBloqueia(orcamento, valorJaConsumido, valorAConsumir){
		log.debug "verificarSeAlertaOuBloqueia"

		if(!orcamento){
			//Caso não tenha nada configurado para o ano, não permite a compra
			log.debug "Orcamento não está configurado!"
			return "Bloqueia";
		} else if(orcamento.valor == 0){
			//Caso o valor configurado seja 0, libera independente do total consumido
			log.debug "Orcamento.valor é igual a zero!"
            return "aprovado";
		} else {
				log.debug "orcamento.valor: "+orcamento.valor

				log.debug "valorJaConsumido: "+valorJaConsumido

				log.debug "valorAConsumir: "+valorAConsumir
			if (orcamento.valor <= (valorJaConsumido + valorAConsumir)){
				//Total configurado menor do que o consumido + valor do pedido

				return orcamento.alertaBloqueio;
			}

			return "aprovado"
		}
	}

	def validarOrcamentoNaEmissao(requestInstance, justificativa){
		def valorACobrar = 0
		if(requestInstance?.tripRequest?.paymentType in ['invoice'] || requestInstance?.tripRequest?.paymentType in ['direct-payment']){
			valorACobrar = requestInstance?.tripRequest?.grandTotal
        } else {
    	//Se a cobrança foi feita com cartão, validar o orçamento apenas do que não será cobrado através do botões de cobrança	
        	//Hotelaria
        	def parceirosComCobrancaMundipagg = ['Hotelli', 'Trend', 'Omnibees']

        	if(requestInstance.temHotel && (!parceirosComCobrancaMundipagg.contains(requestInstance?.hotelRequestData?.origin))){
        		valorACobrar += requestInstance?.tripRequest?.hotelCost
        	}

        	//Aéreo
        	if(requestInstance.temVoo){
        		requestInstance?.flights?.each { def voo ->
        			if(voo?.channel?.name != FORNECEDOR_MILHAS){
        				valorACobrar += voo.price
        			}
        		}
        	}
        }

        log.debug "valorACobrar: "+valorACobrar

        if(valorACobrar == 0 || requestInstance?.tripRequest?.paymentType in ['my-card']){
        	//Se não houver valor a cobrar está aprovado (Assim evita que casos em que não há validação sejam impedidos por budget já estourado)
			return "aprovado"
		}

        return validarOrcamentoDeCadaCentroDeCusto(requestInstance, valorACobrar, justificativa)
	    
	}

	def validarOrcamentoNoLancamentoCartao(requestInstance, valor, justificativa){
		 return validarOrcamentoDeCadaCentroDeCusto(requestInstance, valor, justificativa)
	}

	def registrarLogDeOrcamento(requestInstance, aertaBloqueio, tipoValidacao, valor, justificativa, complementoTipo, centroDeCusto){
			def mesCorrente = null
        	def anoCorrente = new Integer(new Date().format("yyyy"));

        	if(tipoValidacao == "mensal"){
        		mesCorrente = new Integer(new Date().format("MM"));
        	}

        	centroDeCusto = EmpCentroCusto.get(centroDeCusto)

        	def budgetLog = new BudgetLog(
        		operadorAdmin : springSecurityService?.principal?.username,
        		centroDeCusto : centroDeCusto,
        		request : requestInstance,
        		tipo: aertaBloqueio,
				complementoTipo: complementoTipo,
        		justificativa : justificativa,
        		ano : anoCorrente,
        		mes : mesCorrente,
        		valorAtual : valor
        		)

        	budgetLog.save flush:true
    }

    def ratear(requestInstance, valorACobrar){
    	def quantidadeDePassageirosPorCentroDeCusto = [:]
    	log.debug "requestInstance.passageiros: "+requestInstance.passageiros
    	
    	requestInstance.passageiros.each {
    		log.debug "centro de custo id: "+it.centroCusto.id
    		if(quantidadeDePassageirosPorCentroDeCusto[it.centroCusto.id]){
    			quantidadeDePassageirosPorCentroDeCusto[it.centroCusto.id] += 1
    		} else {
    			quantidadeDePassageirosPorCentroDeCusto.put(it.centroCusto.id, 1)
    		}
    	}

    	quantidadeDePassageirosPorCentroDeCusto.each {
    		def fatorRateio = it.value / requestInstance.passageiros.size()
    		it.value = valorACobrar * fatorRateio
    	}

    	return quantidadeDePassageirosPorCentroDeCusto;
    }

	def verificaPermissoesDeAlteracao(requestInstance){
		def permitirInclusaoDeCobrancaExtra = true
		def permitirCancelamento = true
		if(requestInstance.dhEmissao){
			def mesAtual = new Date().format("MM")
			def mesEmissao = requestInstance.dhEmissao?.format("MM")
			
			def anoAtual = new Date().format("yyyy")
			def anoEmissao = requestInstance.dhEmissao?.format("yyyy")
			
			if(mesAtual != mesEmissao || anoAtual != anoEmissao){
				permitirInclusaoDeCobrancaExtra = false
				permitirCancelamento = false
			}
		}

		return ['permitirInclusaoDeCobrancaExtra':permitirInclusaoDeCobrancaExtra,
					'permitirCancelamento':permitirCancelamento]
	}

	def registarCancelamentoEmMesDiferente(requestInstance){
		def permissoesDeAlteracao = verificaPermissoesDeAlteracao(requestInstance)
		if(!permissoesDeAlteracao.permitirCancelamento){
			if(requestInstance.temVoo){
				requestInstance.flights[0].passageirosVoo[0].addToTaxes(
					'type':'CREDITO',
					'price':requestInstance.tripRequest.grandTotal * -1
					)
			}

			if(requestInstance.temHotel){
				def hotelOtherService = new HotelOtherServices(
					hotel: requestInstance.hotel,
					unitPrice: requestInstance.tripRequest.grandTotal * -1,
					total: requestInstance.tripRequest.grandTotal * -1,
					description:'Crédito',
					type:'valorReal'
					)

				hotelOtherService.save(flush:true)
			}

			if(requestInstance.temAluguelCarro()){
				requestInstance.aluguelCarros[0].addToCobrancasAluguelCarro(
					valor: requestInstance.aluguelCarros[0].valorTotal * -1,
					descricao:'Crédito',
					)

				requestInstance.aluguelCarros[0].save(flush:true)
			}
			
			if(requestInstance.outrosServicos){
				requestInstance.outrosServicos[0].addToExtrasOutrosServicos(
					valor: requestInstance.outrosServicos[0].valorTotal * -1,
					descricao:'Crédito',
					)

				requestInstance.outrosServicos[0].save(flush:true)
			}
		}
	}

	def parseReservaEsferaplus(jsonReserva){
		def json = recuperarMockJSON()
		def request = new Request(
			temVoo:true,
			temHotel:false,
			flights:parseVoos(jsonReserva.Segments),
			tripRequest: new TripRequest(
				tipoTarifaVoo : (jsonReserva.Segments.size() > 1 ? "RT" : "OW")
				)
			)

		log.debug "request properties: "+request.properties
		return request
	}

	def parseVoos(jsonSegmentos){
		def voos = []
		def segmentoIda = jsonSegmentos[0];
		
		segmentoIda.Flights.each {
			def voo = parseVoo(it)
			voo.tipo = 'ida'
			voos.add(voo)
		}


		if(jsonSegmentos.size() > 1){
			def segmentoVolta = jsonSegmentos[1];
			segmentoVolta.Flights.each {
			def voo = parseVoo(it)
			voo.tipo = 'volta'
			voos.add(voo)
			}
		}

		return voos
	}

	def parseVoo(jsonVoo){
		log.debug "jsonVoo.ArrivalDateTime: "+jsonVoo.ArrivalDateTime

		log.debug Airlines.findByIata(jsonVoo.CarrierCode.toString())
		def voo = new Flight(
		stops : 0,
		company: Airlines.findByIata(jsonVoo.CarrierCode.toString()).name, //CarrierCode ou OperatedBy
		flightNumber : jsonVoo.FlightNumber,
		departureTime : java.util.Date.parse("yyyy-MM-dd'T'HH:mm:ss", jsonVoo.DepartureDateTime.toString()),
		departureAirport : Airports.findByIata(jsonVoo.DepartureStation.toString()).airportName + " (" + jsonVoo.DepartureStation.toString() + ")",
		arrivalTime : java.util.Date.parse("yyyy-MM-dd'T'HH:mm:ss", jsonVoo.ArrivalDateTime.toString()),
		arrivalAirport : Airports.findByIata(jsonVoo.ArrivalStation.toString()).airportName + " (" + jsonVoo.ArrivalStation.toString() + ")",
		price : 0, //TODO
		localizer : "LOCALIZADOR", //TODO
		ticketNumber : null, //
		conjugateTickets : null,
		fare : 0, //TODO
		boardingTaxes : 0, //TODO
		serviceTaxes : 0, //TODO
		tarifaryBasis : 0, //TODO
		tarifaryBasisText : null, //TODO
		origin : "Esferaplus",
	    reemissao : false,
		deleted : false,
		channel : FlightChannel.get(9), //Esferaplus
		markup : 0,
		dhAtualizacao : new Date(),
		tipoComissao : null,
		valorComissao : null,
		statusTaxaServico : 'pendente',
		
		aeroportoDeChegada : Airports.findByIata(jsonVoo.ArrivalStation),
		aeroportoDeSaida : Airports.findByIata(jsonVoo.DepartureStation),
		companhiaAerea : Airlines.findByIata(jsonVoo.CarrierCode.toString()),
		
		originalFare : 0,
		originalPrice: 0,
		originalBoardingTaxes: 0, 
		originalServiceTaxes : 0
		)

		return voo
	}

	def parsePassageiros(jsonPassageiros){

	}

	def criarTaxasDeTransacaoAereo(requestInstance){
		def voosOrdenados = requestInstance.flights.sort { it.departureTime }
			voosOrdenados.eachWithIndex { def voo, indiceVoo ->
			if(voo.passageirosVoo.size() < requestInstance?.passageirosComVoo?.size()){
					requestInstance.passageiros.each { def passageiro ->
						def passageiroVooCadastrado = PassageiroVoo.findByPassageiroAndFlight(passageiro, voo)
						println "passageiroVooCadastrado"
						println passageiroVooCadastrado
						if(!passageiroVooCadastrado){
							def passageiroVoo = new PassageiroVoo(
								passageiro:passageiro
								)
							if(indiceVoo == 0 && requestInstance.empresa.taxaTransacaoAereo){
								passageiroVoo.addToTaxes(
									'type':'FEE',
									'price':requestInstance.empresa.taxaTransacaoAereo
									)
							}
							voo.addToPassageirosVoo(passageiroVoo)
							//voo.save(flush:true)
						}
					}
				}
			}
	}

	def recuperarMockJSON() {
		File jsonMock = grailsApplication.mainContext.getResource("mockReservaRTMaisDeUmVoo.json").file
		def json = new JsonSlurper().parseText(jsonMock.text)
		
		return json
	}

	// TODO: Esse é o método a ser alterado, definitivamente
	def efetuarPagamento(params){

		def respostaPagamento = false
		def codigoServico = params.prefixo + params.idObjeto
		def requestInstance = Request.get(params.idPedido)
		def empresaNaFatura = "Hotelli"
		def gateway = "Mundipagg"
        def cartaoPedido = obterCartaoCreditoRequest(requestInstance)
        def codigoReferenciaPedido = lancamentoCartaoService.montarCodigoReferenciaPedido(requestInstance.id, codigoServico)

		
		if(requestInstance?.empresa?.controlaOrcamento && requestInstance?.tripRequest?.paymentType != 'my-card'){
			def validacaoOrcamento = validarOrcamentoNoLancamentoCartao(requestInstance, Double.parseDouble(params.valor), params.justificativa)
			
			if(validacaoOrcamento != "aprovado"){
				return validacaoOrcamento
			}
		}

		if(["ASSENTOESPECIAL", "MCOMULTA", "BAGAGEM"].contains(params.tipo)){
			respostaPagamento = ["sucesso":true,"transacao":["codigoDeAutorizacao":' - ']]
			def taxa = FlightTaxes.get(params.idObjeto)
			gateway = taxa.passageiroVoo.flight.company

			if(taxa.passageiroVoo.flight.company.toLowerCase().contains('Gol Linhas'.toLowerCase()) && taxa.passageiroVoo.flight.origin.toLowerCase().contains('Rextur'.toLowerCase())){
				gateway = 'READ*GOL ['+taxa.passageiroVoo.localizer+']';
			}
			empresaNaFatura = taxa.passageiroVoo.flight.company
		} else {
			def valorEmCentavos = (new BigDecimal(params.valor).multiply(100).longValue())
			
				respostaPagamento = lancamentoCartaoService.pagarNoGatewayJSONNovo(codigoReferenciaPedido, cartaoPedido, valorEmCentavos)

		}
		def argumentosParaLog = [params.servico, params.tipo, params.valor, gateway, empresaNaFatura]
		//Atenção com a variavel abaixo
		def pagamentoEfetuadoComSucesso = respostaPagamento.sucesso
		//Atenção com a variavel acima
		
		if(pagamentoEfetuadoComSucesso){
			lancamentoCartaoService.registrarPagamentoDoServico(params)
			
			argumentosParaLog.add(respostaPagamento.transacao.codigoDeAutorizacao)
			argumentosParaLog.add(' - ')
			requestChangeLogService.registrarPagamento(requestInstance,argumentosParaLog,"sucesso")
			
			lancamentoCartaoService.registrarLancamentoCartao(
				requestInstance,
				codigoServico,
				params.double('valor'),
				empresaNaFatura,
				params.servico,
				params.tipo,
                cartaoPedido
			)
		} else {
			def erros = respostaPagamento.erros
			if(!erros){
				erros = respostaPagamento.transacao.resultado
			}
			argumentosParaLog.add(' - ')
			argumentosParaLog.add(erros)
			requestChangeLogService.registrarPagamento(requestInstance,argumentosParaLog,"erro")
			def mensagemDeErro = ""
			if(respostaPagamento.erros instanceof java.lang.String){
				mensagemDeErro = respostaPagamento.erros	
			} else {
				mensagemDeErro = respostaPagamento.erros.join(", ")
			}
			return "Erro ao realizar o pagamento: " + mensagemDeErro
		}
		
		return "Pagamento realizado com sucesso"
	}

	def getCartaoHotelli(requestInstance){
		def cartaoHotelli = null
		def cartaoPedido = null

		if(requestInstance.tripRequest.paymentType == "company-card"){
			if(requestInstance.temVoo()){
				if(requestInstance.tripRequest.idcartaoCreditoFlight){
					cartaoPedido = EmpCartaoCredito.get(requestInstance.tripRequest.idcartaoCreditoFlight)
					if(!cartaoPedido || cartaoPedido?.ebta){
						return null
					} else {
						return EmpCartaoCredito.findEhCartaoHotelliAereo()
					}
				}
			}
			else if(requestInstance.temHotel()){
				if(requestInstance.tripRequest.idcartaoCreditoHotel){
					cartaoPedido = EmpCartaoCredito.get(requestInstance.tripRequest.idcartaoCreditoHotel)
					if(!cartaoPedido || cartaoPedido?.ebta){
						return null
					} else {
						return EmpCartaoCredito.findEhCartaoHotelliHotel()
					}
				}
			}
		} else if(requestInstance.tripRequest.paymentType == "my-card"){
			if(requestInstance.temVoo()){
				return EmpCartaoCredito.findEhCartaoHotelliAereo()
			}
			else if(requestInstance.temHotel()){
				return EmpCartaoCredito.findEhCartaoHotelliHotel()
			}
		}

		return null
	}

	def recuperarFormaDePagamentoParaEmitirLoc(passageiroVooInstance, comCartaoHotelli = false){
		def requestInstance = passageiroVooInstance?.flight?.request
		 def flightInstance = passageiroVooInstance?.flight

		def formaDePagamento = requestInstance?.tripRequest?.paymentType
		 def numeroCartao = ''
		 def cvv = ''
		 def titularCartao = ''
		 def validadeCartaoMes = ''
		 def validadeCartaoAno = ''
		 def bandeiraCartao = ''
		 def valorTotal = passageiroVooInstance?.getValorTaxaUnitario()
		 def tipoPagamento = 'faturado'

		 if(formaDePagamento == 'my-card'){
			 numeroCartao = requestInstance?.tripRequest?.cardNumber
			 cvv = requestInstance?.tripRequest?.cardCode
			 titularCartao = requestInstance?.tripRequest?.cardName
			 validadeCartaoMes = requestInstance?.tripRequest?.cardExpirationMonth
			 validadeCartaoAno = requestInstance?.tripRequest?.cardExpirationYear
			 bandeiraCartao = requestInstance?.tripRequest?.cardBrand
			 tipoPagamento = 'cartao'
		 } else if(formaDePagamento == 'company-card'){
		 	def cartaoCredito = null

			 if(comCartaoHotelli){
			 	cartaoCredito = getCartaoHotelli(requestInstance)
			 } else {
  				 def idcartao

				if(requestInstance?.tripRequest?.idcartaoCredito){
					idcartao = requestInstance?.tripRequest?.idcartaoCredito
				} else if(requestInstance?.tripRequest?.idcartaoCreditoFlight){
					idcartao = requestInstance?.tripRequest?.idcartaoCreditoFlight
				} else {
					idcartao = requestInstance?.tripRequest?.idcartaoCreditoHotel
				}
				
				cartaoCredito = EmpCartaoCredito.get(idcartao)
			}

			if(!cartaoCredito){
				return null
			}
			
			numeroCartao = cartaoCredito?.getNumeroDecript()
			cvv = cartaoCredito?.cvv
			titularCartao = cartaoCredito?.nome
			validadeCartaoMes = cartaoCredito?.validadeMes
			validadeCartaoAno = cartaoCredito?.validadeAno
			bandeiraCartao = cartaoCredito?.bandeira

			if("Amex" == bandeiraCartao){
				bandeiraCartao = "AmericanExpress"
			} else if("Diners" == bandeiraCartao){
				bandeiraCartao = "DinnersClub"
			}
			
			tipoPagamento = 'cartao'
		 }
		 
		 log.debug "numeroCartao: "+numeroCartao
		 log.debug "cvv: "+cvv
		 log.debug "titularCartao: "+titularCartao
		 log.debug "validadeCartaoMes: "+validadeCartaoMes
		 log.debug "validadeCartaoAno: "+validadeCartaoAno
		 log.debug "bandeiraCartao: "+bandeiraCartao
		 log.debug "tipoPagamento: "+tipoPagamento
		 log.debug "valorTotal: "+valorTotal

		 return [
				 "tipoPagamento": tipoPagamento
				,"numeroCartao": numeroCartao
				,"cvv": cvv
				,"titularCartao": titularCartao
				,"validadeCartaoMes": validadeCartaoMes
				,"validadeCartaoAno": validadeCartaoAno
				,"bandeiraCartao": bandeiraCartao
				,"tipoPagamento": tipoPagamento
				,"valorTotal": valorTotal
		 ]
	}

}
