package hotelliadmin



import static org.springframework.http.HttpStatus.*
import grails.converters.JSON
import grails.gorm.DetachedCriteria
import grails.plugin.springsecurity.annotation.Secured
import grails.transaction.Transactional
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

import org.hibernate.criterion.CriteriaSpecification
import java.text.SimpleDateFormat

import pl.touk.excel.export.WebXlsxExporter

@Transactional(readOnly = true)
@Secured(['ROLE_ADMIN', 'ROLE_NO_ROLES'])
class RequestController {
	def emailService
	def requestService
	def pdfRenderingService
	def grailsApplication
	def empFuncionarioService
	def usersService
	def springSecurityService
	def airportsService
	def airlinesService
	def utilService
	def exportService 
	def lancamentoCartaoService
	def requestChangeLogService
	def empresaService
	def esferaplusRequestService
	def twilioService

	static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

	def verificaAcesso(requestInstance){
		if(requestInstance.agenciaTenant?.id != springSecurityService?.principal?.agenciaTenant?.id){
			notFound()
			return
		}
	}

	def index(Integer max) {
		def dateFormat =  message(code: "default.date.format.short")

		params.max = Math.min(max ?: 10, 100)
		params.offset = params.offset ? params.offset : 0
		params.sort = params.sort ? params.sort : "dhRequest"
		params.order = params.order ? params.order: "desc"
		
		def criterioIntervaloDataInicial = obterCriterioIntervaloDataInicial(Request)
        criterioIntervaloDataInicial = criterioIntervaloDataInicial.build {
            tripRequest {
                ne("status.id", 10L)
            }
			eq("agenciaTenant.id", springSecurityService?.principal?.agenciaTenant?.id)
        }


		if(params.idNomeOuFuncionario || params.status || params.periodo || params.dataInicial || params.dataFinal || params.tipoPedido){
			def criteria = Request.createCriteria()
			//new DetachedCriteria(Empresa).build {
			def resultado = criteria.list(){
				resultTransformer org.hibernate.Criteria.DISTINCT_ROOT_ENTITY
				if(params.tipoPedido == 'hotel'){
					createAlias('hotel', 'h', CriteriaSpecification.INNER_JOIN)
                } else {
					createAlias('hotel', 'h', CriteriaSpecification.LEFT_JOIN)
                }

				createAlias('hotelReservationCodes', 'hrc', CriteriaSpecification.LEFT_JOIN)
				createAlias('h.quarto', 'q', CriteriaSpecification.LEFT_JOIN)
				createAlias('q.estabelecimento', 'e', CriteriaSpecification.LEFT_JOIN)

				if(params.tipoPedido == 'locacao'){
                	createAlias('aluguelCarros', 'ac', CriteriaSpecification.INNER_JOIN)
                } else {
					createAlias('aluguelCarros', 'ac', CriteriaSpecification.LEFT_JOIN)
                }

				if(params.tipoPedido == 'aereo'){
					createAlias('flights', 'f', CriteriaSpecification.INNER_JOIN)
                } else {
					createAlias('flights', 'f', CriteriaSpecification.LEFT_JOIN)
                }

				createAlias('f.passageirosVoo', 'pv', CriteriaSpecification.LEFT_JOIN)

				if(params.tipoPedido == 'seguroViagem'){
					createAlias('segurosViagem', 'segurosViagem', CriteriaSpecification.INNER_JOIN)
                }

                if(params.tipoPedido == 'transfer'){
					createAlias('transfers', 'transfers', CriteriaSpecification.INNER_JOIN)
                }

                if(params.tipoPedido == 'passagemRodoviaria'){
					createAlias('passagensRodoviarias', 'passagensRodoviarias', CriteriaSpecification.INNER_JOIN)
                } else {
                	createAlias('passagensRodoviarias', 'passagensRodoviarias', CriteriaSpecification.LEFT_JOIN)
                }

                if(params.tipoPedido == 'outrosServicos'){
					createAlias('outrosServicos', 'outrosServicos', CriteriaSpecification.INNER_JOIN)
                }

                if(!params.sort){
					order('id', 'desc')
				} else {
					order(params.sort, params.order)
				}


				or {
					if(params.idNomeOuFuncionario){
						eq("id", params.long("idNomeOuFuncionario"))
						ilike("destinationCity", "%" + params.idNomeOuFuncionario + "%")
						autor {
							ilike("nome", "%" + params.idNomeOuFuncionario + "%")
						}
						empresa {
								ilike("nome", "%" + params.idNomeOuFuncionario + "%")
						}
						
						or{
							ilike("hrc.codReserva", "%" + params.idNomeOuFuncionario + "%")
						}
						
						or {
							ilike("h.reservationCode", "%" + params.idNomeOuFuncionario + "%")
						}

						or {
							ilike("ac.codigoReserva", "%" + params.idNomeOuFuncionario + "%")
							ilike("ac.descricao", "%" + params.idNomeOuFuncionario + "%")
						}

						or {
							ilike("passagensRodoviarias.codigoReserva", "%" + params.idNomeOuFuncionario + "%")
						}
						
						passageiros {
							or {
								ilike("name", "%" + params.idNomeOuFuncionario + "%")
								ilike("surname", "%" + params.idNomeOuFuncionario + "%")
							}
						}
						
						ilike("pv.localizer", "%" + params.idNomeOuFuncionario + "%")
						ilike("pv.ticketNumber", "%" + params.idNomeOuFuncionario	 + "%")

		                
                		ilike("e.nome", "%" + params.idNomeOuFuncionario + "%")
					}
				}
                                
				if(params.status){
					tripRequest{
						status {
							eq("id", params.long("status"))
						}
					}
				}
				if(params.periodo){
					ge("dhRequest", extrairBuscaPorPeriodo(params.periodo))
				}
				if(params.dataInicial){
					params.dataInicial = params.date('dataInicial', dateFormat)
					ge("dhRequest", params.dataInicial)
				}
				if(params.dataFinal){
					params.dataFinal = params.date('dataFinal', dateFormat)
					le("dhRequest", params.dataFinal)
				}
				eq("agenciaTenant.id", springSecurityService?.principal?.agenciaTenant?.id)
			}

			def filterParams = [
				"idNomeOuFuncionario":params.idNomeOuFuncionario
				,"status":params.status
				,"periodo":params.periodo
				,"tipoPedido":params.tipoPedido
			]

			if(params.dataInicial){
				filterParams.put("dataInicial",params.dataInicial)
			}

			if(params.dataFinal){
				filterParams.put("dataFinal",params.dataFinal)
			}

			log.debug "params.offset: "+params.offset
			log.debug "params.max: "+params.max

			log.debug "resultado.size(): "+resultado.size()

			def ultimoElementoDaPagina = 0
			def resultadoPaginado = []

			if(resultado.size() > 0){
				ultimoElementoDaPagina = (new Integer(params.offset + params.max) > resultado.size()-1 ? resultado.size()-1 : new Integer(params.offset + params.max)-1)
				resultadoPaginado = resultado[new Integer(params.offset)..ultimoElementoDaPagina]
			}

			respond resultadoPaginado,
			model:[requestInstanceCount: resultado.size()
				, filterParams: filterParams
			]
			
			return
		}

		respond criterioIntervaloDataInicial.list(params), model:[requestInstanceCount: criterioIntervaloDataInicial.count()]
	}

	def enviarEmailEmAprovacao(){
		def requestInstance = Request.get(params.id)
		
		render(view: '/email/pedidoEmAprovacaoSolicitante', model: ['requestInstance' : requestInstance])
	}
	
	def extrairBuscaPorPeriodo(periodo){
		def data = new Date()
		data.set(second: 0, minute: 0, hourOfDay: 0)

		switch(periodo){
			case 'hoje':
				return data
				break
			case 'ontem':
				return data.minus(1)
				break
			case 'ultimos7Dias':
				return data.minus(7)
				break
			case 'ultimos15Dias':
				return data.minus(15)
				break
			case 'ultimos30Dias':
				return data.minus(30)
				break
			case 'mesCorrente':
				data = data.toCalendar()
				data.set(Calendar.DAY_OF_MONTH, 1)
				return data.getTime()
				break
		}
	}


	@Transactional
	def testeLancamentoCartao(Request requestInstance) {
		requestService.registrarPagamentoDoAereo(requestInstance)
		
	}

	@Transactional
	def show(Request requestInstance) {
		verificaAcesso(requestInstance)
	
		def voucherHotel = false
		def valorTotalPorLocalizador = [:]
		def logsRequest = requestService.recuperarLogs(requestInstance)
		def logsPayment = requestService.recuperarPaymentLogs(requestInstance);
		def dadosTaxasDeServico = empresaService.recuperarMapaTaxasDeServico(requestInstance.empresa)
		def dadosTaxaDeServicoJSON = dadosTaxasDeServico as JSON
		def permissoesDeAlteracao = requestService.verificaPermissoesDeAlteracao(requestInstance)
		
		if(requestInstance.temHotel){
			if(requestService.recuperarVoucherDoHotel(requestInstance))
			voucherHotel = true
		}
		
		if(requestInstance.temVoo){
			valorTotalPorLocalizador = requestService.recuperarValorTotalPorLocalizador(requestInstance)
			requestService.criarTaxasDeTransacaoAereo(requestInstance)
		}

		if(!requestInstance.informacoesImportantes){
			requestInstance.informacoesImportantes = new InformacoesImportantes('texto':'-')
			requestInstance.save(flush:true)
		}
		

		def centrosDeCusto = EmpCentroCusto.findAll {
			empresa == requestInstance.empresa
	    }
		
		
		def idCartao
		def empCartaoCreditoInstance

		if(requestInstance?.temVoo && requestInstance?.tripRequest?.idcartaoCreditoFlight){
			idCartao = requestInstance?.tripRequest?.idcartaoCreditoFlight		
		} else if(requestInstance?.temHotel && requestInstance?.tripRequest?.idcartaoCreditoHotel){
			idCartao = requestInstance?.tripRequest?.idcartaoCreditoHotel
		} else {
			idCartao = requestInstance?.tripRequest?.idcartaoCredito
		}
		
		if(idCartao){
			empCartaoCreditoInstance = EmpCartaoCredito.get(idCartao)
		}
		
		def cartaoHotelli = requestService.getCartaoHotelli(requestInstance)

		respond requestInstance, model:['empCartaoCreditoInstance': empCartaoCreditoInstance, 
		'voucherHotel': voucherHotel, 
		'logsRequest':logsRequest, 
		'logsPayment':logsPayment, 
		'centrosDeCusto':centrosDeCusto, 
		'valorTotalPorLocalizador':valorTotalPorLocalizador, 
		'dadosTaxaDeServicoJSON':dadosTaxaDeServicoJSON, 
		'permitirInclusaoDeCobrancaExtra':permissoesDeAlteracao.permitirInclusaoDeCobrancaExtra, 
		'permitirCancelamento':permissoesDeAlteracao.permitirCancelamento,
		'cartaoHotelli':cartaoHotelli
		]
	}
	
	@Transactional
	def json(Request requestInstance) {
		def testeRequest = Request.get(params.id)
		render testeRequest.hotelRequestData as JSON
	}

	def create() {
		def requestInstance = new Request(params)
		requestInstance.temVoo = true
		requestInstance.temHotel = true
		respond requestInstance, model:['tiposRequest': traduzirTiposRequest()]
	}
	
	private traduzirTiposRequest() {
		def tiposRequest = [:]
		
		Request.listarTiposRequest().each {
			tiposRequest << [(it): message(code: "request.tipo.${it}")]
		}
		
		tiposRequest
	}

	@Transactional
	def duplicar(Request requestInstance) {
		def requestDuplicado = requestInstance.clone()
		requestDuplicado.id = null


		requestDuplicado.informacoesImportantes = null

		requestDuplicado.hotelPrices = []

		requestDuplicado.hotelTaxes = []

		requestDuplicado.hotelReservationCodes = []

		requestDuplicado.auditingOtherFlights = []

		requestDuplicado.auditingOtherHotel = []

		requestDuplicado.lancamentosCartao = []

		requestDuplicado.aprovadores = []

		requestDuplicado.passageiros = []

		requestDuplicado.flights = []

		requestDuplicado.aluguelCarros = []

		requestDuplicado.segurosViagem = []

		requestDuplicado.transfers = []

		requestDuplicado.passagensRodoviarias = []

		requestDuplicado.outrosServicos = []

		requestDuplicado.tripRequest = null
		requestDuplicado.dhRequest = new Date()
		requestDuplicado.dhAtualizacao = new Date()
		requestDuplicado.dhCancelamento = null
		requestDuplicado.dhEmissao = null

		requestDuplicado.origemCriacao = 'admin'
		requestDuplicado.origemAlteracao = null
		requestDuplicado.origemEmissao = null

		requestDuplicado.save(flush:true)

		requestDuplicado.tripRequest = requestInstance.tripRequest.clone()
		requestDuplicado.tripRequest.id = null
		requestDuplicado.tripRequest.approving = null
		requestDuplicado.tripRequest.paymentType = 'invoice'
		requestDuplicado.tripRequest.dhApproval = null
		requestDuplicado.tripRequest.status = Status.get(3)

		requestDuplicado.tripRequest.request = requestDuplicado
		requestDuplicado.save(flush:true)

		if(requestDuplicado.hotelRequestData){
			requestDuplicado.hotelRequestData = requestInstance.hotelRequestData.clone()
			if(!requestDuplicado.hotelRequestData.roomtype){
				requestDuplicado.hotelRequestData.roomtype = "";
			}

			if(!requestDuplicado.hotelRequestData.providerbookingcode){
				requestDuplicado.hotelRequestData.providerbookingcode = "";
			}

			requestDuplicado.hotelRequestData.id = null

			requestDuplicado.hotelRequestData.request = requestDuplicado
			requestDuplicado.save(flush:true)
		}

		if(requestDuplicado.hotel){
			requestDuplicado.hotel = requestInstance.hotel.clone()
			requestDuplicado.hotel.otherServices = []
			requestDuplicado.hotel.reservationCode = null
			requestDuplicado.hotel.status = "pendente"
			requestDuplicado.hotel.id = null

			requestDuplicado.hotel.request = requestDuplicado
			requestDuplicado.save(flush:true, failOnError:true)

				if(requestDuplicado.temHotel && requestDuplicado.empresa.taxaTransacaoHotel){
				for(def indiceQuarto = 0; indiceQuarto < requestDuplicado.numRooms; indiceQuarto++){
					def hotelOtherService = new HotelOtherServices(
						hotel: requestDuplicado.hotel,
						unitPrice: requestDuplicado.empresa.taxaTransacaoHotel,
						total: requestDuplicado.empresa.taxaTransacaoHotel,
						description:'Taxa de transação',
						type:'valorReal'
						)

					hotelOtherService.save(flush:true)
				}
			}
		}

		requestInstance.flights.each {
			def flightDuplicado = it.clone()
			flightDuplicado.id = null
			flightDuplicado.localizer = null
			flightDuplicado.ticketNumber = null
			flightDuplicado.internalLocalizer = null
			flightDuplicado.statusTaxaServico = 'pendente'
			flightDuplicado.passageirosVoo = []

			requestDuplicado.addToFlights(flightDuplicado)
		}

		requestInstance.segurosViagem.each {
			def objDuplicado = it.clone()
			objDuplicado.id = null
			objDuplicado.dtCriacao = new Date()
			objDuplicado.dtAtualizacao = new Date()
		    objDuplicado.status = "pendente"

			requestDuplicado.addToSegurosViagem(objDuplicado)
		}

		requestInstance.transfers.each {
			def objDuplicado = it.clone()
			objDuplicado.id = null
			objDuplicado.dtCriacao = new Date()
			objDuplicado.dtAtualizacao = new Date()
		    objDuplicado.status = "pendente"

			requestDuplicado.addToTransfers(objDuplicado)
		}

		requestInstance.passagensRodoviarias.each {
			def objDuplicado = it.clone()
			objDuplicado.id = null
			objDuplicado.dtCriacao = new Date()
			objDuplicado.dtAtualizacao = new Date()
		    objDuplicado.status = "pendente"

			requestDuplicado.addToPassagensRodoviarias(objDuplicado)
		}

		requestInstance.outrosServicos.each {
			def objDuplicado = it.clone()
			objDuplicado.id = null
			objDuplicado.dtCriacao = new Date()
			objDuplicado.dtAtualizacao = new Date()
		    objDuplicado.status = "pendente"

			objDuplicado.extrasOutrosServicos = []

			requestDuplicado.addToOutrosServicos(objDuplicado)
		}

		requestInstance.aluguelCarros.each {
			def objDuplicado = it.clone()
			objDuplicado.id = null
			objDuplicado.dtCriacao = new Date()
			objDuplicado.dtAtualizacao = new Date()
		    objDuplicado.status = "pendente"

			objDuplicado.cobrancasAluguelCarro = []

			requestDuplicado.addToAluguelCarros(objDuplicado)
		}

		println "REQUEST"
		println requestDuplicado.properties

		requestDuplicado.save(flush:true, failOnError:true)

		def requestChangeLog = new RequestChangeLog(
			operator		: springSecurityService?.principal?.username,
			dhChange		: new Date(),
			fieldChanged	: '-',
			oldValue		: '-',
			newValue		: "Criado a partir do pedido " + requestInstance.id,
			objectId		: requestDuplicado.id,
			object			: 'request',
			fieldChangedCode: 'request.log.pedidoCriado'
		)
		requestChangeLog.save(flush:true)

		requestDuplicado.save(flush:true, failOnError:true)

		request.withFormat {
			form multipartForm {
				flash.message = message(code: 'default.created.message', args: [
					message(code: 'request.label', default: 'Request'),
					requestDuplicado.id
				])
				redirect requestDuplicado
			}
			'*' { respond requestDuplicado, [status: CREATED] }
		}

	}

	@Transactional
	def save(Request requestInstance) {
		requestInstance.carregarCabecalho()
		requestInstance.agenciaTenant = requestInstance.empresa?.agenciaTenant

		if (requestInstance == null) {
			notFound()
			return
		}
		
		
		log.debug "APROVADORES: "+request.aprovadores
		
		requestInstance.validate()
		if (requestInstance.hasErrors()) {
			def centrosDeCusto = EmpCentroCusto.findAll {
				empresa == requestInstance.empresa
			}
			
			params.auditoriaHabilitada = requestInstance.empresa.auditoriaHabilitada
			respond requestInstance.errors, view:'create', model:['requestInstance': requestInstance, 'tiposRequest': traduzirTiposRequest(), 'centrosDeCusto': centrosDeCusto, 'parametros': params, 'createWithLoc': params.createWithLoc ? true : false]
			return
		}
		
		
		if(requestInstance?.tripRequest?.paymentType == 'company-card' && params.idCartaoEmpresa){
			requestInstance?.tripRequest?.idcartaoCredito = params.int('idCartaoEmpresa')
			requestInstance?.tripRequest?.idcartaoCreditoHotel = params.int('idCartaoEmpresa')
			requestInstance?.tripRequest?.idcartaoCreditoFlight = params.int('idCartaoEmpresa')
		} else if(requestInstance?.tripRequest?.paymentType == 'my-card'){
			requestInstance?.tripRequest?.cardBrand = params.bandeiraCartaoPessoal
			requestInstance?.tripRequest?.cardName = params.nomeCartaoPessoal
			requestInstance?.tripRequest?.cardNumber = params.numeroCartaoPessoal
			requestInstance?.tripRequest?.cardCode = params.codigoSegurancaCartaoPessoal
			requestInstance?.tripRequest?.cardExpirationMonth = params.mesValidadeCartaoPessoal
			requestInstance?.tripRequest?.cardExpirationYear = params.anoValidadeCartaoPessoal
		} 
//		else if(requestInstance?.tripRequest?.paymentType == 'company-approval'){
//			requestInstance?.tripRequest?.status = Status.get(2)
//		}

		requestInstance?.carregarCabecalho()
		requestInstance.save flush:true
		
		
		def fieldChanged = message(code: "request.log.pedidoCriado")
		
		def requestChangeLog = new RequestChangeLog(
			operator		: springSecurityService?.principal?.username,
			dhChange		: new Date(),
			fieldChanged	: '-',
			oldValue		: '-',
			newValue		: '-',
			objectId		: requestInstance.id,
			object			: 'request',
			fieldChangedCode: 'request.log.pedidoCriado'
		)
		requestChangeLog.save(flush:true)

		if(requestInstance.temHotel && requestInstance.empresa.taxaTransacaoHotel){
			for(def indiceQuarto = 0; indiceQuarto < requestInstance.numRooms; indiceQuarto++){
				def hotelOtherService = new HotelOtherServices(
					hotel: requestInstance.hotel,
					unitPrice: requestInstance.empresa.taxaTransacaoHotel,
					total: requestInstance.empresa.taxaTransacaoHotel,
					description:'Taxa de transação',
					type:'valorReal'
					)

				hotelOtherService.save(flush:true)
			}
		}
		
		def passageirosNovos = requestInstance.passageiros.findAll() { !it.temReferencia }
		usersService.cadastrarNovosUsuarios(passageirosNovos, requestInstance.empresa)
		
		request.withFormat {
			form multipartForm {
				flash.message = message(code: 'default.created.message', args: [
					message(code: 'request.label', default: 'Request'),
					requestInstance.id
				])
				redirect requestInstance
			}
			'*' { respond requestInstance, [status: CREATED] }
		}
	}

	def edit(Request requestInstance) {
		verificaAcesso(requestInstance)

		respond requestInstance
	}
    
    private obterVoosExclusao(params, requestInstance) {
        def idsVoos = params.findAll { k, v ->
            k ==~ /flights\[\d+\]\.id/ && v
        }.collect { k, v ->
            params.long(k)
        }
        
        return requestInstance?.flights?.findAll {
            it.id && !(it.id in idsVoos)
        }
    }

	@Transactional
	def update(Request requestInstance) {
		verificaAcesso(requestInstance)

		if(params.observacaoAtendimento){
			requestChangeLogService.registrarObservacaoAtendimento(requestInstance, params.observacaoAtendimento)
		}
		
		if(params.aprovadoresAlterados){
			def aprovadoresSelecionados = requestInstance?.aprovadores
			def aprovadoreComId = []
			def aprovadoreSemId = []
			
			aprovadoresSelecionados.each {
				if(it?.id){
					aprovadoreComId.add(it)
				
				} else {
					aprovadoreSemId.add(it)
				}
			}
			
			aprovadoreSemId.each {
				if(!aprovadoreComId.aprovador.id.contains(it.aprovador.id)){
					requestInstance.addToAprovadores(it)
				} else {
					requestInstance.removeFromAprovadores(it)
				}
			}
			
			aprovadoreComId.each {
				if(!aprovadoreSemId.aprovador.id.contains(it.aprovador.id)){
					requestInstance.removeFromAprovadores(it)
					it?.delete()
				}
			}		
		}
		
		if (requestInstance == null) {
			notFound()
			return
		}
		
		if (!params.boolean("temHotel")) {
			requestService.removerHotel(requestInstance)
		} else {
			def auxOtherServices = []
			requestInstance.hotel.otherServices.each { def servico ->
				servico.dtLastUpdate = new Date();
				if(servico.deleted){
					servico.delete()
				} else {
					auxOtherServices.add(servico)
				}
			}
			
			requestInstance.hotel.otherServices = auxOtherServices
		}
		
		
		if (requestInstance.aluguelCarros){
			requestInstance.aluguelCarros = utilService.apagarItensDaLista(requestInstance.aluguelCarros	)

			requestInstance.aluguelCarros.each { def aluguelCarro ->
				def auxCobrancas = []
					aluguelCarro.cobrancasAluguelCarro?.each { def cobranca ->
					cobranca.dtAtualizacao = new Date();
					if(cobranca.deleted){
						cobranca.delete()
					} else {
						auxCobrancas.add(cobranca)
					}
				}
				
				aluguelCarro.cobrancasAluguelCarro = auxCobrancas
			}
		}
		
		
		requestInstance.segurosViagem = utilService.apagarItensDaLista(requestInstance.segurosViagem)
		requestInstance.flights = utilService.apagarItensDaLista(requestInstance.flights)
		requestInstance.transfers = utilService.apagarItensDaLista(requestInstance.transfers)
		requestInstance.outrosServicos = utilService.apagarItensDaLista(requestInstance.outrosServicos)
		
		
		log.debug "ID cartão empresa: "+params.idCartaoEmpresa
		
		if(requestInstance?.tripRequest?.paymentType == 'company-card' && params.idCartaoEmpresa){
			requestInstance?.tripRequest?.idcartaoCredito = params.int('idCartaoEmpresa')
			requestInstance?.tripRequest?.idcartaoCreditoHotel = params.int('idCartaoEmpresa')
			requestInstance?.tripRequest?.idcartaoCreditoFlight = params.int('idCartaoEmpresa')
		} 
		else if(requestInstance?.tripRequest?.paymentType == 'my-card'){
			requestInstance?.tripRequest?.cardBrand = params.bandeiraCartaoPessoal ?:requestInstance?.tripRequest?.cardBrand
			requestInstance?.tripRequest?.cardName = params.nomeCartaoPessoal ?:requestInstance?.tripRequest?.cardName
			requestInstance?.tripRequest?.cardNumber = params.numeroCartaoPessoal ?:requestInstance?.tripRequest?.cardNumber
			requestInstance?.tripRequest?.cardCode = params.codigoSegurancaCartaoPessoal ?:requestInstance?.tripRequest?.cardCode
			requestInstance?.tripRequest?.cardExpirationMonth = params.mesValidadeCartaoPessoal ?:requestInstance?.tripRequest?.cardExpirationMonth
			requestInstance?.tripRequest?.cardExpirationYear = params.anoValidadeCartaoPessoal ?:requestInstance?.tripRequest?.cardExpirationYear
		 }

		log.debug "PARAMS"
		log.debug params		
		def listaDeVoos = requestInstance.flights
		log.debug "LISTA DE VOOS: "+listaDeVoos

		listaDeVoos.each { def voo ->
			def listaDePassageiros = voo.passageirosVoo
			log.debug "Número do voo: "+voo.flightNumber
			log.debug "PassageiroVoo: "+voo.passageirosVoo

			listaDePassageiros.each { def passageiroVoo ->
				def listaDeTaxas = passageiroVoo.taxes
				log.debug "listaDeTaxas: " + listaDeTaxas
				listaDeTaxas.each { def taxa ->
					log.debug "TAXA: " + taxa 
					if(taxa.deleted){
						 passageiroVoo.removeFromTaxes(taxa)
						 taxa.delete()
					}
				}
				passageiroVoo.taxes = listaDeTaxas
			}
			voo.passageirosVoo = listaDePassageiros
		}
		requestInstance.flights = listaDeVoos
		requestInstance.dhAtualizacao = new Date()
		requestInstance.validate()
		log.debug "requestInstance.errors: "+requestInstance.errors

		if (requestInstance.hasErrors()) {
			def valorTotalPorLocalizador = requestService.recuperarValorTotalPorLocalizador(requestInstance)
			def dadosTaxasDeServico = empresaService.recuperarMapaTaxasDeServico(requestInstance.empresa)
			def dadosTaxaDeServicoJSON = dadosTaxasDeServico as JSON
			def permissoesDeAlteracao = requestService.verificaPermissoesDeAlteracao(requestInstance)

			respond requestInstance.errors, view:'show', model:['valorTotalPorLocalizador':valorTotalPorLocalizador, 'dadosTaxaDeServicoJSON':dadosTaxaDeServicoJSON, 'permitirInclusaoDeCobrancaExtra':permissoesDeAlteracao.permitirInclusaoDeCobrancaExtra, 'permitirCancelamento':permissoesDeAlteracao.permitirCancelamento]
			return
		}
		
		def voucher = null
		if(requestInstance?.temHotel()){
			voucher = requestService.recuperarVoucherDoHotel(requestInstance)
		}

		if(voucher && requestInstance?.temHotel){
			// if(requestInstance.hotel.dirtyPropertyNames.contains('reservationCode')){
			// 	voucher.codSeguranca = Voucher.findByCodSeguranca(requestInstance.hotel.dirtyPropertyNames['reservationCode'])
			// }

			def noites = 0
			
			use(groovy.time.TimeCategory) {
				def duration = requestInstance.hotel.checkout - requestInstance.hotel.checkin
				noites = duration.days
				// println duration
				// if(duration.hours >= 23){
				// 	noites += 1
				// }

				// if(duration.hours <= 1 && duration.hours != 0){
				// 	noites -= 1
				// }
			}
			
			if(noites == 0)
				noites = 1

			
			voucher.funcionario 	= requestInstance.autor
			voucher.quarto			= requestInstance.hotel.quarto
			voucher.valor			= requestInstance.tripRequest.hotelCost
			voucher.dtReserva		= requestInstance.hotel.checkin
			voucher.quartos			= requestInstance.numRooms
			voucher.taxa			= requestInstance.tripRequest.taxes
			voucher.codSeguranca	= requestInstance.hotel.reservationCode
			voucher.numDiarias		= noites
			
			voucher.save(flush:true)
		}

		requestInstance?.dhAtualizacao = new Date()
		requestInstance?.carregarCabecalho()
		requestInstance.save flush:true
		
		params.motivoCortesia.eachWithIndex  { value, index ->
			if(value){
				def requestChangeLog = new RequestChangeLog(
					operator		: springSecurityService?.principal?.username,
					dhChange		: new Date(),
					fieldChanged	: 'Cortesia para o voo '+params.vooMotivoCortesia[index],
					oldValue		: '-',
					newValue		: value+' - VOO '+params.vooMotivoCortesia[index],
					objectId		: requestInstance.id,
					object			: 'request',
					fieldChangedCode: 'Cortesia'
				)
				requestChangeLog.save(flush:true)
			}
		}

		
		request.withFormat {
			form multipartForm {
				flash.message = message(code: 'default.updated.message', args: [
					message(code: 'Request.label', default: 'Request'),
					requestInstance.id
				])
				redirect requestInstance
			}
			'*'{ respond requestInstance, [status: OK] }
		}
	}

	def rateio(Request requestInstance){
		def rateio = requestService.validarOrcamentoDeCadaCentroDeCusto(requestInstance, 300)

		render rateio as JSON
	}

	@Transactional
	def voltarParaEmitido(Request requestInstance) {
		if (requestInstance == null) {
			notFound()
			return
		}

		verificaAcesso(requestInstance)

		if(requestInstance.tripRequest.status?.id == 8){
			respond requestInstance.errors, view:'show'
			return
		}

		def requestLog = new RequestLog(
			operador: springSecurityService?.principal?.username,
			acao : 'Voltar para emitido',
			dhAcao: new Date(),
			request: requestInstance,
			detalhes : '-'
		)

		requestLog.validate()

		if(requestLog.hasErrors()){
			log.debug "errors: "+requestLog.errors
		}

		requestLog.save flush:true

		requestInstance?.dhAtualizacao = new Date()
		requestInstance?.origemAlteracao = 'admin'
		requestInstance?.tripRequest?.status = Status.get(8)


		requestInstance?.validate()
		if (requestInstance.hasErrors()) {
			respond requestInstance.errors, view:'show'
			return
		}

		requestInstance.save flush:true

		request.withFormat {
			form multipartForm {
				flash.message = message(code: 'default.updated.message', args: [
					message(code: 'Request.label', default: 'Request'),
					requestInstance.id
				])
				redirect requestInstance
			}
			'*'{ respond requestInstance, [status: OK] }
		}
	}

	@Transactional
	def voltarParaAprovado(Request requestInstance) {
		if (requestInstance == null) {
			notFound()
			return
		}
		
		verificaAcesso(requestInstance)

		if(requestInstance.tripRequest.status?.id == 4){
			respond requestInstance.errors, view:'show'
			return
		}

		def requestLog = new RequestLog(
			operador: springSecurityService?.principal?.username,
			acao : 'Voltar para aprovado',
			dhAcao: new Date(),
			request: requestInstance,
			detalhes : '-'
		)

		requestLog.validate()

		if(requestLog.hasErrors()){
			log.debug "errors: "+requestLog.errors
		}

		requestLog.save flush:true

		requestInstance?.dhAtualizacao = new Date()
		requestInstance?.origemAlteracao = 'admin'
		requestInstance?.tripRequest?.status = Status.get(4)


		requestInstance?.validate()
		if (requestInstance.hasErrors()) {
			respond requestInstance.errors, view:'show'
			return
		}

		requestInstance.save flush:true

		request.withFormat {
			form multipartForm {
				flash.message = message(code: 'default.updated.message', args: [
					message(code: 'Request.label', default: 'Request'),
					requestInstance.id
				])
				redirect requestInstance
			}
			'*'{ respond requestInstance, [status: OK] }
		}
	}

	@Transactional
	def emitir(Request requestInstance) {
		if (requestInstance == null) {
			notFound()
			return
		}

		if(requestInstance.tripRequest.status?.id == 8){
			respond requestInstance.errors, view:'show'
			return
		}

		requestInstance?.dhAtualizacao = new Date()

		if(params.observacaoAtendimento){
			requestChangeLogService.registrarObservacaoAtendimento(requestInstance, params.observacaoAtendimento)
		}

		requestInstance?.validate()
		if (requestInstance.hasErrors()) {
			println "TEM ERROS!"
			respond requestInstance.errors, view:'show'
			return
		}


		def idCartao
		def empCartaoCreditoInstance


		if(requestInstance?.temVoo && requestInstance?.tripRequest?.idcartaoCreditoFlight){
			idCartao = requestInstance?.tripRequest?.idcartaoCreditoFlight		
		} else if(requestInstance?.temHotel && requestInstance?.tripRequest?.idcartaoCreditoHotel){
			idCartao = requestInstance?.tripRequest?.idcartaoCreditoHotel
		} else {
			idCartao = requestInstance?.tripRequest?.idcartaoCredito
		}
		
		println "ID CARTão @@@@"
		println idCartao

		if(idCartao){
			empCartaoCreditoInstance = EmpCartaoCredito.get(idCartao)
		}

		def cartaoHotelli = requestService.getCartaoHotelli(requestInstance)
		def hotel = Hotel.findByRequest(requestInstance)
		//Novo Fluxo, caso tenha cartao hotelli e o pagamento seja com cartão, é obrigatória a cobrança na mndipgg antes da emissão
		if(requestInstance?.temHotel && (params?.hotelRequestData?.channel == 'Expedia' || params?.hotelRequestData?.channel == 'Decolar') 
			&& cartaoHotelli 
			&& ['my-card', 'company-card'].contains(requestInstance?.tripRequest?.paymentType) 
			&& hotel.status == 'pendente'){
				respond requestInstance.errors, view:'show', model:['requestInstance':requestInstance, 'erroHotelCard': 'erro-hotelcard','permitirInclusaoDeCobrancaExtra':0, 'permitirCancelamento':0, 'dadosTaxaDeServicoJSON': '{}', 'empCartaoCreditoInstance':empCartaoCreditoInstance]
				return
		}

		if(requestInstance?.temHotel && (params?.hotelRequestData?.channel == 'Expedia' || params?.hotelRequestData?.channel == 'Decolar')){
			if(requestInstance?.tripRequest?.paymentType == 'company-card' && (!empCartaoCreditoInstance?.cvv || new Integer(empCartaoCreditoInstance?.cvv) == 0) && hotel.status == 'pendente'){
				respond requestInstance.errors, view:'show', model:['requestInstance':requestInstance, 'erroHotelCard': 'erro-hotelcard','permitirInclusaoDeCobrancaExtra':0, 'permitirCancelamento':0, 'dadosTaxaDeServicoJSON': '{}', 'empCartaoCreditoInstance':empCartaoCreditoInstance]
				return
			}
		}

		def validacao = requestService.validarEmissao(requestInstance)

		if(validacao == true){
			def voosExclusao = obterVoosExclusao(params, requestInstance)
			requestService.removerListaVoos(requestInstance, voosExclusao)
			def listaDeVoos = requestInstance.flights
			listaDeVoos.each { def voo ->
				def listaDePassageiros = voo.passageirosVoo
				listaDePassageiros.each { def passageiroVoo ->
					def listaDeTaxas = passageiroVoo.taxes
					listaDeTaxas.each { def taxa ->
						if(taxa.deleted){
							 passageiroVoo.removeFromTaxes(taxa)
							 taxa.delete()
						}
					}
					passageiroVoo.taxes = listaDeTaxas
				}
				voo.passageirosVoo = listaDePassageiros
			}
			requestInstance.flights = listaDeVoos
			
			if (!params.boolean("temHotel")) {
				requestService.removerHotel(requestInstance)
			}

			
			if(requestInstance?.empresa?.controlaOrcamento && requestInstance?.tripRequest?.paymentType != 'my-card'){
				def validacaoOrcamento = requestService.validarOrcamentoNaEmissao(requestInstance, params.justificativa);
				if(validacaoOrcamento != "aprovado"){
					def valorTotalPorLocalizador = requestService.recuperarValorTotalPorLocalizador(requestInstance)
					def dadosTaxasDeServico = empresaService.recuperarMapaTaxasDeServico(requestInstance.empresa)
					def dadosTaxaDeServicoJSON = dadosTaxasDeServico as JSON
					def permissoesDeAlteracao = requestService.verificaPermissoesDeAlteracao(requestInstance)
				

					respond requestInstance.errors, view:'show', model:['erroOrcamento': validacaoOrcamento, 'valorTotalPorLocalizador':valorTotalPorLocalizador, 'dadosTaxaDeServicoJSON':dadosTaxaDeServicoJSON, 'permitirInclusaoDeCobrancaExtra':permissoesDeAlteracao.permitirInclusaoDeCobrancaExtra, 'permitirCancelamento':permissoesDeAlteracao.permitirCancelamento]
					return
				}
			}

			requestService.emitir(requestInstance)
			
			emailService.enviarEmailVoucher(requestInstance)
			emailService.enviarEmailConfirmacaoReservaParaEstabelecimento(requestInstance)
			
			if(requestInstance.temHotel){
				requestService.registrarComissaoDeHotel(requestInstance)
			}
			
			if(requestInstance.aluguelCarros){
				requestService.registrarComissaoDeLocacao(requestInstance)
			}
			
			if(requestInstance.segurosViagem){
				requestService.registrarComissaoDeSeguroDeViagem(requestInstance)
			}
			
			if(requestInstance?.tripRequest?.paymentType in ['my-card', 'company-card']){
				if(requestInstance.temHotel){
					cartaoHotelli = requestService.getCartaoHotelli(requestInstance)
					def lancamentoCartaoDeDiariaHotelli = LancamentoCartao.findByRequestAndTipoAndEmpresaFatura(requestInstance, 'Diária', 'Hotelli')
					def lancamentoCartaoDeDiariaFornecedor = LancamentoCartao.findByRequestAndTipoAndEmpresaFatura(requestInstance, 'Diária', 
						requestInstance.hotelRequestData.origin)
					def parceirosSemRegistroDeCartaoHotelli = ['Hotelli', 'Trend']

					if(requestInstance?.hotel?.status == 'pendente'){
						requestService.registrarPagamentoDeHotel(requestInstance)
					} else if(requestInstance?.hotel?.status == 'pago' && cartaoHotelli 
						&& lancamentoCartaoDeDiariaHotelli && !lancamentoCartaoDeDiariaFornecedor
						&& !parceirosSemRegistroDeCartaoHotelli.contains(requestInstance?.hotelRequestData?.origin)) {
						requestService.registrarPagamentoDeHotel(requestInstance, cartaoHotelli)
					}

				}

				
				if(requestInstance.temVoo){
					requestService.registrarPagamentoDoAereo(requestInstance)
				}
			}

			request.withFormat {
				form multipartForm {
					flash.message = message(code: 'default.updated.message', args: [
						message(code: 'Request.label', default: 'Request'),
						requestInstance.id
					])
					redirect requestInstance
				}
				'*'{ respond requestInstance, [status: OK] }
			}
		} else {
			respond requestInstance.errors, view:'show', model: ['erroValidacaoEmissao' :validacao]
			return
		}
	}

	@Transactional
	def efetuarPagamento(){
		render requestService.efetuarPagamento(params)
	}

	@Transactional
	def validarOrcamento(Request requestInstance){
		def validacaoOrcamento = requestService.validarOrcamentoNaEmissao(requestInstance)
		
		respond requestInstance, model:['erroOrcamento': 'orcamento-'+validacaoOrcamento], view:"show"
		return

		def mapaRetorno = ['erroOrcamento': 'orcamento-'+validacaoOrcamento]
		render mapaRetorno as JSON
	}
	
	@Transactional
	def enviarParaAprovacao(Request requestInstance){
		verificaAcesso(requestInstance)

		requestInstance?.tripRequest?.status = Status.get(2)
		def aprovadoresSemEmail = []
		def erro = ""

		if(requestInstance.empresa.temAlcadaAprovacao){
			def ultimaAlcadaDeAprovacao = requestInstance.aprovadores.aprovador.nivelAlcadaAprovacao.max()	
			requestInstance.aprovadores = requestInstance.aprovadores.findAll {
				it.aprovador.nivelAlcadaAprovacao == ultimaAlcadaDeAprovacao
			}

			requestInstance.save()
		}

		println "requestInstance.aprovadores"
		println requestInstance.aprovadores
		
		requestInstance.aprovadores.each {
			log.debug "TOKEN: "+it.token

			if(!it.aprovador.email){
				aprovadoresSemEmail = aprovador.add(it.aprovador.nome + ' :' + it.aprovador.email);
			}
			
			it.token = AesCryptor.encode(it.aprovador.email + '-' + requestInstance.id)
			it.aprovou = null
		}

		if (aprovadoresSemEmail.size() > 0) {
			erro = ["Existem aprovadores e-mail: " + aprovadoresSemEmail.join(',')]
		}


		def retornoEnviarService = emailService.enviarEmailDeSolicitacaoDeAprovacao(requestInstance)
		log.debug "retornoEnviarService: "+retornoEnviarService

		if(retornoEnviarService){
			erro = "Ocorreu um erro ao enviar o e-mail de solicitação de aprovação"
		}

		log.debug "ERRO enviarEmailDeSolicitacaoDeAprovacao: "+erro

		if(retornoEnviarService){
			erro = "Ocorreu um erro ao enviar o e-mail de solicitação de aprovação"
		}

		log.debug "ERRO enviarEmailDeSolicitacaoDeAprovacao: "+erro

		def retornoEnviarMensagemService = twilioService.enviarMensagemDeSolicitacaoDeAprovacao(requestInstance)
		log.debug "retornoEnviarMensagemService: "+ retornoEnviarMensagemService.join(";")

		if(erro){
			def valorTotalPorLocalizador = requestService.recuperarValorTotalPorLocalizador(requestInstance)
			def dadosTaxasDeServico = empresaService.recuperarMapaTaxasDeServico(requestInstance.empresa)
			def dadosTaxaDeServicoJSON = dadosTaxasDeServico as JSON
			def permissoesDeAlteracao = requestService.verificaPermissoesDeAlteracao(requestInstance)
			

			respond requestInstance, view:'show', model:['erroValidacaoEmissao':erro, 'valorTotalPorLocalizador':valorTotalPorLocalizador, 'dadosTaxaDeServicoJSON':dadosTaxaDeServicoJSON, 'permitirInclusaoDeCobrancaExtra':permissoesDeAlteracao.permitirInclusaoDeCobrancaExtra, 'permitirCancelamento':permissoesDeAlteracao.permitirCancelamento]
			return
		}

		requestInstance?.origemAlteracao = 'admin'
		requestInstance?.tripRequest.paymentType = 'company-approval'
		requestInstance?.tripRequest.approving = null
		requestInstance?.tripRequest.dhApproval = null
		requestInstance?.tripRequest.centroDeCusto = null

		requestInstance.save flush:true
		
		new RequestLog(
			operador: springSecurityService?.principal?.username,
			acao : 'Envio para aprovação',
			dhAcao: new Date(),
			request: requestInstance,
			detalhes : 'Aprovadores: '+requestInstance.aprovadores.aprovador.nome.join(', ')
		).save()
		
		request.withFormat {
			form multipartForm {
				flash.message = message(code: 'default.updated.message', args: [
					message(code: 'Request.label', default: 'Request'),
					requestInstance.id
				])
				redirect requestInstance
			}
			'*'{ respond requestInstance, [status: OK] }
		}
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def enviarEmailDeAprovacao(){
		def requestInstance = Request.get(params.id);
		def retorno = ["sucesso":false, "mensagem":"O pedido " + params.id + " informado não pôde ser encontrado"]
		if(requestInstance){
			try {
				emailService.enviarEmailDeSolicitacaoDeAprovacao(requestInstance)

				def retornoEnviarMensagemService = twilioService.enviarMensagemDeSolicitacaoDeAprovacao(requestInstance)
				log.debug "twilioService.enviarMensagemDeSolicitacaoDeAprovacao: "+ retornoEnviarMensagemService.join(";")

				response.status = 200
				retorno.sucesso = true
				retorno.mensagem = null
				render retorno as JSON
			} catch (Exception ex){
				response.status = 500
				retorno.mensagem = "Ocorreu um erro ao enviar os e-mails dos vouchers do pedido " + params.id + ". "+ex.getMessage()
				render retorno as JSON
			}
			return
		} else {
			response.status = 404
			render retorno as JSON
			return
		}
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def enviarEmailRecebemosSeuPedido(){

		def requestInstance = Request.get(params.id)
		def informacoesImportantes
		def noites = 0
		if(requestInstance?.temHotel){
			use(groovy.time.TimeCategory) {
				def duration = requestInstance.hotel.checkout - requestInstance.hotel.checkin
				noites = duration.days
			}
			
			if(noites == 0) {
				noites = 1
			}
		}

		informacoesImportantes = requestInstance.informacoesImportantes.texto ? requestInstance.informacoesImportantes.texto.replaceAll("<(.|\n)p?>", '<br/>') : null

		emailService.enviarEmailRecebemosSeuPedido(requestInstance, noites, informacoesImportantes)
		
		//render(view: '/email/recebemosSeuPedido', model: ['requestInstance' : requestInstance, 'noites':noites])
	}
	
	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def enviarEmailConfirmacaoReservaParaEstabelecimento(){
		def requestInstance = Request.get(params.id);
		def retorno = ["sucesso":false, "mensagem":"O pedido " + params.id + " informado não pôde ser encontrado"]
		if(requestInstance){
			try {
				emailService.enviarEmailConfirmacaoReservaParaEstabelecimento(requestInstance)
				response.status = 200
				retorno.sucesso = true
				retorno.mensagem = null
				render retorno as JSON
			} catch (Exception ex){
				response.status = 500
				retorno.mensagem = "Ocorreu um erro ao enviar os e-mails dos vouchers do pedido " + params.id + ". "+ex.getMessage()
				render retorno as JSON
			}
			return
		} else {
			response.status = 404
			render retorno as JSON
			return
		}
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def enviarEmailConfirmacao(){
		def requestInstance = Request.get(params.id);
		def retorno = ["sucesso":false, "mensagem":"O pedido " + params.id + " informado não pôde ser encontrado"]
		if(requestInstance){
			try {
				emailService.enviarEmailVoucher(requestInstance)
				response.status = 200
				retorno.sucesso = true
				retorno.mensagem = null
				render retorno as JSON
			} catch (Exception ex){
				response.status = 500
				retorno.mensagem = "Ocorreu um erro ao enviar os e-mails dos vouchers do pedido " + params.id + ". "+ex.getMessage()
				render retorno as JSON
			}
			return
		} else {
			response.status = 404
			render retorno as JSON
			return
		}
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def enviarEmailPedidoRecusado(){
		def requestInstance = Request.get(params.id);
		def retorno = ["sucesso":false, "mensagem":"O pedido " + params.id + " informado não pôde ser encontrado"]

		if(requestInstance){
			try {
				emailService.enviarEmailDeNotitifacacaoDeRecusa(requestInstance)
				response.status = 200
				retorno.sucesso = true
				retorno.mensagem = null
				render retorno as JSON
			} catch (Exception ex){
				response.status = 500
				retorno.mensagem = "Ocorreu um erro ao enviar o e-mail de recusa referente ao pedido " + params.id + ". "+ex.getMessage()
				render retorno as JSON
			}
			return
		} else {
			response.status = 404
			render retorno as JSON
			return
		}
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def enviarEmailPedidoAprovado(){
		def requestInstance = Request.get(params.id);

		//render(view: '/email/pedidoAprovadoSolicitante', model:['requestInstance':requestInstance])
		//return

		def retorno = ["sucesso":false, "mensagem":"O pedido " + params.id + " informado não pôde ser encontrado"]

		if(requestInstance){
			try {
				emailService.enviarEmailDeNotitifacacaoDeAprovacao(requestInstance)
				response.status = 200
				retorno.sucesso = true
				retorno.mensagem = null
				render retorno as JSON
			} catch (Exception ex){
				response.status = 500
				retorno.mensagem = "Ocorreu um erro ao enviar o e-mail de pedido aprovado referente ao pedido " + params.id + ". "+ex.getMessage()
				render retorno as JSON
			}
			return
		} else {
			response.status = 404
			render retorno as JSON
			return
		}
	}
	
	@Transactional
	def delete(Request requestInstance) {

		if (requestInstance == null) {
			notFound()
			return
		}

		verificaAcesso(requestInstance)

		requestInstance.delete flush:true

		request.withFormat {
			form multipartForm {
				flash.message = message(code: 'default.deleted.message', args: [
					message(code: 'Request.label', default: 'Request'),
					requestInstance.id
				])
				redirect action:"index", method:"GET"
			}
			'*'{ render status: NO_CONTENT }
		}
	}

	protected void notFound() {
		request.withFormat {
			form multipartForm {
				flash.message = message(code: 'default.not.found.message', args: [
					message(code: 'request.label', default: 'Request'),
					params.id
				])
				redirect action: "index", method: "GET"
			}
			'*'{ render status: NOT_FOUND }
		}
	}
	
	@Transactional
	def cancelar(Request requestInstance){
		verificaAcesso(requestInstance)

		requestService.cancelarTaxasAereas(requestInstance)
		requestInstance?.tripRequest?.status = Status.get(7)
		requestInstance?.dhAtualizacao = new Date()
		requestInstance?.dhCancelamento = new Date()
		def lancamentosCartao = LancamentoCartao.findAllByRequest(requestInstance)
		lancamentosCartao*.status = "cancelado"
		lancamentosCartao*.save flush:true

		
		def requestChangeLog = new RequestChangeLog(
			operator		: springSecurityService?.principal?.username,
			dhChange		: new Date(),
			fieldChanged	: '-',
			oldValue		: '-',
			newValue		: 'Cancelado',
			objectId		: requestInstance.id,
			object			: 'request',
			fieldChangedCode: params.motivoCancelamento
		)
		requestChangeLog.save(flush:true)

		requestService.registarCancelamentoEmMesDiferente(requestInstance)
		
		request.withFormat {
			form multipartForm {
				flash.message = message(code: 'default.updated.message', args: [
					message(code: 'Request.label', default: 'Request'),
					requestInstance.id
				])
				redirect requestInstance
			}
			'*'{ respond requestInstance, [status: OK] }
		}
	}

	def export() {
		def criterioIntervaloDataInicial = tratarParametrosDeExportacao(RelatorioHotelaria, params)
		def dadosParaExportacao = criterioIntervaloDataInicial.list(sort:"pedido", order:"desc")
        
        exportarRelatorio(RelatorioHotelaria, dadosParaExportacao)
	}
    
    def relatorioAereo() {       
		def dateFormat =  message(code: "default.date.format.short")
		def dadosParaExportacao = exportService.recuperarDadosParaExportacaoAereo(params, dateFormat)
		
        exportarRelatorio(RelatorioAereo, dadosParaExportacao)
    }
    
    def relatorioLocacaoVeiculos() {
        def dateFormat = message(code: "default.date.format.short")
		def dadosParaExportacao = exportService.recuperarDadosParaExportacaoDeLocacao(params, dateFormat)
        
        exportarRelatorio(RelatorioLocacaoVeiculos, dadosParaExportacao)
    }

	def relatorioOutrosServicos() {
		def dateFormat =  message(code: "default.date.format.short")
		def dadosParaExportacao = exportService.recuperarDadosParaExportacaoDeOutrosServicos(params, dateFormat)
        
        exportarRelatorio(RelatorioOutrosServicos, dadosParaExportacao)
	}
    
    private exportarRelatorio(classeRelatorio, dadosParaExportacao) {       
        def relatorioBuilder = new RelatorioBuilder(classeRelatorio: classeRelatorio)
		def cabecalhosTraduzidos = []
        def cabecalhos = relatorioBuilder.getCabecalhosRelatorio()
        
        cabecalhosTraduzidos = cabecalhos.collect {
            message(code: it)
        }
        
		WebXlsxExporter webXlsxExporter = new WebXlsxExporter()
		webXlsxExporter.setWorksheetName(relatorioBuilder.getTituloRelatorio())
        
		webXlsxExporter.with {
			setResponseHeaders(response, relatorioBuilder.getPrefixoArquivoRelatorio() + new Date().format('yyyy-MM-dd_hh-mm-ss') + ".xlsx")
			fillHeader(cabecalhosTraduzidos)
			add(dadosParaExportacao, relatorioBuilder.getCamposRelatorio());
			save(response.outputStream)
		}
    }

	private DetachedCriteria obterCriterioIntervaloDataInicial(clazz) {
		def dataIntervaloInicial = new SimpleDateFormat("dd/MM/yyyy").parse(grailsApplication.config.hotelli.pedido.dataIntervaloInicial)
		return new DetachedCriteria(clazz).build {
			gt("dhRequest", dataIntervaloInicial)
		}
	}
	
	private DetachedCriteria tratarParametrosDeExportacao(clazz, parametros) {
		def dateFormat =  message(code: "default.date.format.short")
		def dataIntervaloInicial = new SimpleDateFormat("dd/MM/yyyy").parse(grailsApplication.config.hotelli.pedido.dataIntervaloInicial)
		return new DetachedCriteria(clazz).build {
			gt("dhCriacao", dataIntervaloInicial)
			
			if(parametros.periodo){
				ge("dhCriacao", extrairBuscaPorPeriodo(parametros.periodo))
			}
			
			if(parametros.dataInicial){
				parametros.dataInicial = parametros.date('dataInicial', dateFormat)
				ge("dhCriacao", parametros.dataInicial)
			}
			
			if(parametros.dataFinal){
				parametros.dataFinal = parametros.date('dataFinal', dateFormat)
				le("dhCriacao", parametros.dataFinal)
			}
			
		}
	}
	
	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def voucherHotel(){
		def requestInstance = Request.get(params.id)
		
		def voucher = requestService.recuperarVoucherDoHotel(requestInstance)
		def logo = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets + requestInstance.empresa.agenciaTenant.mapConfiguracoes['nomeArquivoLogoVoucher']))
		// //def codigoDeBarras = "https://www.hotellicorporate.com/drawbarcode?code=$voucher.codSeguranca".toURL().getBytes()
		// def arquivoVoucher = grailsApplication.config.caminhoVouchers + 'voucherHotel' + params.id + '.pdf'
		renderPdf(template: "/voucher/hotel", model:['requestInstance': requestInstance, 'voucher':voucher, 'logo':logo.bytes])
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def voucherVoo(){
		def requestInstance = Request.get(params.id)
		println requestInstance.empresa.agenciaTenant.mapConfiguracoes

		def logo = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets + requestInstance.empresa.agenciaTenant.mapConfiguracoes['nomeArquivoLogoVoucher']))
		
		def logosCompanhiasAereas = [:]
		def valorTotalMulta = 0
		requestInstance.flights.each {
			def multas = it.passageirosVoo.taxes.flatten().findAll() { def taxas ->  taxas.type == "MCOMULTA" }
			
			if(multas){
				valorTotalMulta += multas.price.sum()
			}

			def companhia =  it.company
			def logoCompanhiaAerea = requestService.getLogoCompanhiasAereas(it)

			try {
				if(logoCompanhiaAerea) {
					logoCompanhiaAerea = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets + logoCompanhiaAerea))
				}
				logosCompanhiasAereas.put(companhia, logoCompanhiaAerea.bytes)
			} catch(e){
				logosCompanhiasAereas.put(companhia, null)
			}
		}
		


		render(template: "/voucher/voo", model:['requestInstance': requestInstance, 'logo':logo.bytes, 'logosCompanhiasAereas': logosCompanhiasAereas, 'valorTotalMulta':valorTotalMulta])
	}
	
	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def downloadRecibo(params, prefixo){
		def passageiroVoo = PassageiroVoo.get(params.id)
		def lancamentoCartao
		def requestInstance = passageiroVoo.flight.request
		
		if(prefixo == 'TAX'){
			lancamentoCartao = LancamentoCartao.findByOrderReference(requestInstance?.id+'-'+prefixo+params.id)
		} else {
			lancamentoCartao = LancamentoCartao.findByCodigoServico(prefixo+params.id)
		}
		
		log.debug "Params: "+params
		log.debug "Prefixo: "+prefixo
		log.debug "ParametroVoo: "+passageiroVoo
		log.debug "Prefixo + ID: "+prefixo+params.id
		log.debug "Lancamento cartao: "+lancamentoCartao

		def arquivoVoucher = grailsApplication.config.caminhoAssets +'reciboFee' + params.id + '.pdf'
		def logo = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets + requestInstance.empresa.agenciaTenant.mapConfiguracoes['nomeArquivoLogoVoucher']))
		//def codigoDeBarras = "https://www.hotellicorporate.com/drawbarcode?code=$voucher.codSeguranca".toURL().getBytes()
//		render(template: "/voucher/reciboFee", model:['requestInstance':requestInstance, 'flightTax': flightTax, 'logo':logo.bytes])
		renderPdf(template: "/voucher/reciboFee", model:['requestInstance':requestInstance, 'lancamentoCartao':lancamentoCartao, 'passageiroVoo': passageiroVoo, 'logo':logo.bytes], filename: arquivoVoucher)
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def downloadReciboFee(){
		downloadRecibo(params, "TAX")
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def downloadReciboVoo(){
		downloadRecibo(params, "VOO")
	}

	@Transactional
	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def downloadVoucherHotel(){
		def requestInstance = Request.get(params.id)
		def arquivoVoucher = grailsApplication.config.caminhoAssets +'voucherHotel' + params.id + '.pdf'
		def voucher = requestService.recuperarVoucherDoHotel(requestInstance)
		if(!voucher){
			def retornoCriacaoVoucher = requestService.verificaDadosDeHotelParaEmitir(requestInstance)
			if(retornoCriacaoVoucher){
				requestInstance.tripRequest.status = Status.get(8)
				requestInstance.tripRequest.save(flush:true)
				voucher = requestService.recuperarVoucherDoHotel(requestInstance)
			}
		}
		def logo = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets + requestInstance.empresa.agenciaTenant.mapConfiguracoes['nomeArquivoLogoVoucher']))
		//def codigoDeBarras = "https://www.hotellicorporate.com/drawbarcode?code=$voucher.codSeguranca".toURL().getBytes()
		renderPdf(template: "/voucher/hotel", model:['requestInstance': requestInstance, 'voucher':voucher, 'logo':logo.bytes], filename: arquivoVoucher)
	}
	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def downloadVoucherIndividual(){
		def requestInstance = Request.get(params.id)
		def passageiro = Passageiro.get(params.idpassageiro)
		def tipoPedido = requestInstance?.hotel ? 'hotelaria': 'aéreo'
		
		log.debug "RequestInstance: "+requestInstance
		log.debug "Passageiro: "+passageiro
		
		
		def voucher = requestService.gerarVoucherIndividual(requestInstance, passageiro)

		response.setContentType("application/octet-stream")
		response.setHeader("Content-disposition", "attachment;filename=voucher-${tipoPedido}-${requestInstance?.id}.pdf")
		response.outputStream << voucher[0].newInputStream()
	}
	
	def downloadVoucherEstabelecimento(){
		def requestInstance = Request.get(params.id)
		verificaAcesso(requestInstance)
		def empresaInstance = requestInstance?.empresa
		def hotelInstance = requestInstance?.hotel
		def quartoInstance = hotelInstance?.quarto
		def estabelecimentoInstance = quartoInstance?.estabelecimento
		def voucher = requestService.recuperarVoucherDoHotel(requestInstance)
		def pedidoInstance = voucher?.pedido
		def logo = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets + requestInstance.empresa.agenciaTenant.mapConfiguracoes['nomeArquivoLogoVoucher']))
	
		def arquivoVoucher = grailsApplication.config.caminhoAssets +'voucherEstabelecimento' + params.id + '.pdf'
		//def voucher = requestService.recuperarVoucherDoHotel(requestInstance)
		//def codigoDeBarras = "https://www.hotellicorporate.com/drawbarcode?code=$voucher.codSeguranca".toURL().getBytes()
		//renderPdf(template: "/voucher/estabelecimento", model:['requestInstance': requestInstance, 'hotelInstance': hotelInstance, 'quartoInstance': quartoInstance, 'estabelecimentoInstance': estabelecimentoInstance, 'logo':logo.bytes], filename: arquivoVoucher)
		//render (template: "/voucher/estabelecimento", model:['requestInstance': requestInstance, 'hotelInstance': hotelInstance, 'quartoInstance': quartoInstance, 'estabelecimentoInstance': estabelecimentoInstance, 'voucher': voucher, 'pedidoInstance': pedidoInstance, 'empresaInstance':empresaInstance])
		renderPdf(template: "/voucher/voucherEstabelecimento", model:['requestInstance': requestInstance, 'hotelInstance': hotelInstance, 'quarto': quartoInstance, 'estabelecimento': estabelecimentoInstance, 'voucher': voucher, 'pedido': pedidoInstance, 'empresaInstance':empresaInstance], filename: arquivoVoucher)
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def downloadVoucherVoo(){
		def requestInstance = Request.get(params.id)
		def arquivoVoucher = grailsApplication.config.caminhoVouchers + 'voucherVoos' + params.id + '.pdf'
		def logo = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets + requestInstance.empresa.agenciaTenant.mapConfiguracoes['nomeArquivoLogoVoucher']))
		
		def logosCompanhiasAereas = [:]
		def valorTotalMulta = 0

		requestInstance.flights.each {
			def companhia =  it.company
			def logoCompanhiaAerea = requestService.getLogoCompanhiasAereas(it)

			def multas = it.passageirosVoo.taxes.flatten().findAll() { def taxas ->  taxas.type == "MCOMULTA" }

			if(multas){
				valorTotalMulta += multas.price.sum()
			}

			try {
				if(logoCompanhiaAerea) {
					logoCompanhiaAerea = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets + logoCompanhiaAerea))
				}
				logosCompanhiasAereas.put(companhia, logoCompanhiaAerea.bytes)
			} catch(e){
				logosCompanhiasAereas.put(companhia, null)
			}
		}

		renderPdf(template: "/voucher/voo", model:['requestInstance': requestInstance, 'logosCompanhiasAereas':logosCompanhiasAereas, 'logo':logo.bytes, 'valorTotalMulta':valorTotalMulta], filename: arquivoVoucher)
	}

	def reenviarEmailVoucher(){
		def requestInstance = Request.get(params.id)
		verificaAcesso(requestInstance)
		emailService.enviarEmailVoucher(requestInstance)

		flash.message = message(code: 'request.emailEnviado')
		redirect requestInstance
	}

	@Transactional
	def reenviarEmailDeSolicitacaoDeAprovacao(){
		def requestInstance = Request.get(params.id)
		verificaAcesso(requestInstance)
		if(requestInstance.empresa.temAlcadaAprovacao){
			def ultimaAlcadaDeAprovacao = requestInstance.aprovadores.aprovador.nivelAlcadaAprovacao.max()	
			requestInstance.aprovadores = requestInstance.aprovadores.findAll {
				it.aprovador.nivelAlcadaAprovacao == ultimaAlcadaDeAprovacao
			}

			requestInstance.save()
		}

		requestInstance.aprovadores.each {
			it.token = AesCryptor.encode(it.aprovador.email + '-' + requestInstance.id)
			it.save flush:true
		}

		emailService.enviarEmailDeSolicitacaoDeAprovacao(requestInstance)

		def retornoEnviarMensagemService = twilioService.enviarMensagemDeSolicitacaoDeAprovacao(requestInstance)
		log.debug "retornoEnviarMensagemService: "+ retornoEnviarMensagemService.join(";")
		
		flash.message = message(code: 'request.emailEnviado')
		redirect requestInstance
	}

	def teste(){
		def requestInstance = Request.get(params.id)
		def funcionario = EmpFuncionario.get(params.idfuncionario)
		def urlSite = funcionario.empSetor.empFilial.empresa.agenciaTenant.mapConfiguracoes['homePlataforma']
    	def token = AesCryptor.encode(funcionario.email)
		token = URLEncoder.encode(token, "UTF-8")
		def link = urlSite+'/redefinir-senha?token='+token

		render view: '/email/redefinirSenhaSite', model:['funcionario' : funcionario, 'link':link]

			return
		def logo = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets + requestInstance.empresa.agenciaTenant.mapConfiguracoes['nomeArquivoLogoVoucher']))
		//def codigoDeBarras = (grailsApplication.config.urlBaseSite + "/drawbarcode?code=43759463").toURL().getBytes()
		def arquivoVoucher = grailsApplication.config.caminhoVouchers + 'voucherVoos' + params.id + '.pdf'

		def logosCompanhiasAereas = [:]

		requestInstance.flights.each {
			def companhia =  it.company
			def logoCompanhiaAerea = requestService.getLogoCompanhiasAereas(it)

			try {
				if(logoCompanhiaAerea) {
					logoCompanhiaAerea = new File(servletContext.getRealPath(grailsApplication.config.caminhoAssets + logoCompanhiaAerea))
				}
				logosCompanhiasAereas.put(companhia, logoCompanhiaAerea.bytes)
			} catch(e){
				logosCompanhiasAereas.put(companhia, null)
			}
		}

		render(template: "/voucher/voo", model:['requestInstance': requestInstance, 'logosCompanhiasAereas': logosCompanhiasAereas, 'logo':logo.bytes])
	}
	
	def autocompletePassageiros(){
		def listaDeFuncionarios = empFuncionarioService.recuperarFuncionariosAutoComplete(params)
		//def listaDeUsuarios = usersService.recuperarUsuariosAutoComplete(params)
		def listaAutoComplete = listaDeFuncionarios //+ listaDeUsuarios
		listaAutoComplete = listaAutoComplete.sort { it.label }
		
		render listaAutoComplete as JSON
	}
	
	def autocompleteAeroportos(){
		def listaDeAeroportos = airportsService.recuperarAeroportosAutoComplete(params)
		
		render listaDeAeroportos as JSON
	}
	
	def autocompleteCompanhias(){
		def listaDeCompanhias = airlinesService.recuperarCompanhiasAutoComplete(params)
		
		render listaDeCompanhias as JSON
	}
	
	def formCartaoPessoal(){
		render(view : '_cartaoPessoal')
	}

	@Transactional
	def createWithLoc(Request requestInstance){
		if(request.method == 'GET') {
			render(view : 'createWithLoc')
			return
		}


		if(request.method == 'POST'){
			try {
				def localizadorOWIdaRT = null
				def credencialOWIdaRT = null

				def localizadorOWVolta = null
				def credencialOWVolta = null

				if(params["seleciona-tarifa"] == "seleciona-tarifa-rt-ow"){	
					localizadorOWIdaRT = params["loc-rt-one-way"]
					credencialOWIdaRT = params["credencial-rt-one-way"]
				} else {
					localizadorOWIdaRT = params["loc-ida-volta-ida"]
					credencialOWIdaRT = params["credencial-ida-volta-ida"]
					localizadorOWVolta = params["loc-ida-volta-volta"]
					credencialOWVolta = params["credencial-ida-volta-volta"]
				}

				// def localizadorOWIdaRT = 'AAEWGLM'
				// def localizadorOWVolta = 'AAEWGLP'
				// def params = [:]
				// params["empresa"] = Empresa.get(70)
				// params.autor = EmpFuncionario.get(52863)

				// def request = new Request(empresa: params.empresa, autor: params.autor)
				if(requestInstance?.tripRequest?.paymentType == 'company-card' && params.idCartaoEmpresa){
					requestInstance?.tripRequest?.idcartaoCredito = params.int('idCartaoEmpresa')
					requestInstance?.tripRequest?.idcartaoCreditoHotel = params.int('idCartaoEmpresa')
					requestInstance?.tripRequest?.idcartaoCreditoFlight = params.int('idCartaoEmpresa')
				} else if(requestInstance?.tripRequest?.paymentType == 'my-card'){
					requestInstance?.tripRequest?.cardBrand = params.bandeiraCartaoPessoal
					requestInstance?.tripRequest?.cardName = params.nomeCartaoPessoal
					requestInstance?.tripRequest?.cardNumber = params.numeroCartaoPessoal
					requestInstance?.tripRequest?.cardCode = params.codigoSegurancaCartaoPessoal
					requestInstance?.tripRequest?.cardExpirationMonth = params.mesValidadeCartaoPessoal
					requestInstance?.tripRequest?.cardExpirationYear = params.anoValidadeCartaoPessoal
				} 

				requestInstance = esferaplusRequestService.carregarPedidoComLocalizadoresEsferaplus(requestInstance, localizadorOWIdaRT, credencialOWIdaRT, localizadorOWVolta, credencialOWVolta)

				// params["empresaAutoComplete"] = params.empresa
				// params["empresa"] = params.empresa.id


			requestInstance.validate()
			requestInstance.flights*.validate()
			requestInstance.tripRequest.validate()
			requestInstance.aprovadores*.validate()

			log.debug "RequestInstance Aprovadores: "+requestInstance.aprovadores
			requestInstance.aprovadores.each {
				if(it.hasErrors()){
						it.errors.each { def erro ->
							log.debug "ERRRO DO requestInstance.tripRequest"
							log.debug erro
						}
					}
			}

			if (requestInstance.hasErrors() || requestInstance.tripRequest.hasErrors()) {


				if(requestInstance.hasErrors()){
					requestInstance.errors.each {
						log.debug "ERRO DO requestInstance.tripRequest: "+it
					}
				}

				if(requestInstance.tripRequest.hasErrors()){
					requestInstance.tripRequest.errors.each {
						log.debug "ERRO DO requestInstance.tripRequest: "+it
					}
				}
			

				respond requestInstance.errors, view:'create', model:['tiposRequest': traduzirTiposRequest(), 'parametros': params, 'createWithLoc' : true]
				return
			}

			requestInstance?.carregarCabecalho()
			requestInstance.save flush:true

		def requestChangeLog = new RequestChangeLog(
			operator		: springSecurityService?.principal?.username,
			dhChange		: new Date(),
			fieldChanged	: 'Pedido criado com localizador',
			oldValue		: '-',
			newValue		: '-',
			objectId		: requestInstance.id,
			object			: 'request',
			fieldChangedCode: 'request.log.pedidoCriado'
		)
		requestChangeLog.save(flush:true)


			requestInstance.passageiros.each { def passageiro -> 
				requestInstance.flights.each { def voo ->
					def informacoesDoVooDeIda = (voo.tipo == "ida" || requestInstance.tripRequest.tipoTarifaVoo.contains("RT"))
					def passageiroVoo = new PassageiroVoo(
						passageiro: passageiro,
						ticketNumber: (informacoesDoVooDeIda ? passageiro.ticketNumber : passageiro.ticketNumberVolta),
						localizer: (informacoesDoVooDeIda ? passageiro.localizador: passageiro.localizadorVolta)
						)

					voo.addToPassageirosVoo(passageiroVoo)
				}
			}

			requestInstance.save flush:true
		

			def idCartao
			def empCartaoCreditoInstance
			if(requestInstance?.tripRequest?.idcartaoCredito){
				idCartao = requestInstance?.tripRequest?.idcartaoCredito		
			} else if(requestInstance?.tripRequest?.idcartaoCreditoHotel){
				idCartao = requestInstance?.tripRequest?.idcartaoCreditoHotel
			} else {
				idCartao = requestInstance?.tripRequest?.idcartaoCreditoFlight
			}
			
			if(idCartao){
				empCartaoCreditoInstance = EmpCartaoCredito.get(idCartao)
			}

			respond requestInstance, view:'show', model:['empCartaoCreditoInstance': empCartaoCreditoInstance,'tiposRequest': traduzirTiposRequest(), 'parametros': params, 'createWithLoc' : true]
			return
			} catch (Exception ex){
				render(view : 'createWithLoc', model:['erros':ex.getMessage(), 'parametros': params])
				return
			}
		}
	}

	@Transactional(readOnly = false)
	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def cancelarPedidosExpirados(){
		def pedidosVoo = Request.findAll{
			tripRequest {
				status {
					id == 2
				}
			}
			flights {
				departureTime <= new Date()
			}
		}

		def pedidosHotel = Request.findAll {
			tripRequest {
				status {
					id == 2
				}
			}
			hotel {
				checkin <= new Date().minus(1)
			}
		}

		def pedidosHotelSemCheckinAntesDaCriacao = []
		pedidosHotel.each { pedidoHotel ->
			if(pedidoHotel.dhRequest < pedidoHotel.hotel.checkin){
				pedidosHotelSemCheckinAntesDaCriacao.add(pedidoHotel)
			}
		}

		println pedidosHotelSemCheckinAntesDaCriacao

		def pedidosSeguroViagem = Request.findAll {
			tripRequest {
				status {
					id == 2
				}
			}
			segurosViagem {
				dtInicio <= new Date().minus(1)
			}
		}

		def pedidosAluguelCarro = Request.findAll {
			tripRequest {
				status {
					id == 2
				}
			}
			aluguelCarros {
				checkin <= new Date().minus(1)
			}
		}

		def pedidosPassagemRodoviaria = Request.findAll {
			tripRequest {
				status {
					id == 2
				}
			}
			passagensRodoviarias {
				dtInicio <= new Date().minus(1)
			}
		}

		def pedidosTransfer = Request.findAll {
			tripRequest {
				status {
					id == 2
				}
			}
			transfers {
				dtInicio <= new Date().minus(1)
			}
		}

		def pedidosOutroServico = Request.findAll {
			tripRequest {
				status {
					id == 2
				}
			}
			outrosServicos {
				dtInicio <= new Date().minus(1)
			}
		}


		def pedidosASeremCancelados = [
			pedidosVoo, 
			pedidosHotelSemCheckinAntesDaCriacao, 
			pedidosSeguroViagem, 
			pedidosAluguelCarro, 
			pedidosPassagemRodoviaria,
			pedidosTransfer,
			pedidosOutroServico
		].flatten()

		pedidosASeremCancelados.each {
			it.tripRequest.status = Status.get(7)
			println "-=-=-=--=-=-="
			println it.tripRequest.errors
			it.tripRequest.save(flush:true)

			new RequestChangeLog(
				dhChange : new Date(),
				operator : 'admin',
				object   : 'request',
				objectId : it.id,
				fieldChanged : 'Cancelamento Automático',
				oldValue	 : 'Pendente Aguardando Aprovação',
				newValue	 : 'Cancelado',
				fieldChangedCode : 'Cancelamento Automático'

			).save(flush:true)
		}


		render pedidosASeremCancelados.id as JSON
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def enviarEmailDeVoucherNaVespera(){
        def amanha = new Date().plus(1)
        def dataInicial = Date.parse("yyyy-MM-dd hh:mm", amanha.format('yyyy-MM-dd 00:00'))
        def dataFinal = Date.parse("yyyy-MM-dd hh:mm", amanha.format('yyyy-MM-dd 23:59'))

		 def resultado = PassageiroVoo.withCriteria {
            flight {
                ge("departureTime", dataInicial)
                le("departureTime", dataFinal)
                request {
                	tripRequest {
                		status {
                			eq("id", new Long(8))
                		}
                	}
                }
            }
            isNotNull('ticketNumber')
        }

        def pedidos = resultado.flight.request.unique().flatten()
        println pedidos
        pedidos.each {
        	println "AEEEEEEEEEEEEEEEEEEEEEEE"
        	println it
			//emailService.enviarEmailVoucher(it)
        }
        def retorno = ['sucesso':true, 'pedidos':pedidos.id]
        render retorno as JSON
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	pagarNoGatewayJSON(){
	//TODOWL
		def parametros = request.JSON
		println parametros
		def codigoPedido = parametros.codigoPedido
		def cartao = parametros.cartao
		def valor = parametros.valor
		render lancamentoCartaoService.pagarNoGatewayJSONNovo(codigoPedido, cartao, valor) as JSON
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def estornarNoGatewayJSON(){
		def parametros = request.JSON
		println parametros
		def chaveDaCobranca = parametros.chaveDaCobranca
		render lancamentoCartaoService.estornarNoGatewayJSON(chaveDaCobranca) as JSON
	}

	@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
	def testeTwilio(Request requestInstance){
		println twilioService.enviarMensagemDeSolicitacaoDeAprovacao(requestInstance)
	}
}
