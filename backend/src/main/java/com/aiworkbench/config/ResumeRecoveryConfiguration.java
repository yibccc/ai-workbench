package com.aiworkbench.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Finite reservations must be recovered after startup too. */
@Configuration(proxyBeanMethods=false)
@EnableScheduling
public class ResumeRecoveryConfiguration {}
