package br.com.sicredi.creditservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface OperacaoSocioJpaRepository extends JpaRepository<OperacaoSocioJpaEntity, UUID> {}
