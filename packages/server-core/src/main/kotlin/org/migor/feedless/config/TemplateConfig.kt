package org.migor.feedless.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer

@Configuration
class TemplateConfig {

  @Bean
  fun createFreeMarkerConfigurer(): FreeMarkerConfigurer {
    val configurer = FreeMarkerConfigurer()
    configurer.setTemplateLoaderPath("classpath:/markup-templates")
    configurer.setDefaultEncoding("UTF-8")
    return configurer
  }
}
