package xln.main

import org.springframework.context.ConfigurableApplicationContext

import scala.reflect.ClassTag

trait SparkRunner {

  //def run[T: ClassTag]() : Unit
  def run(context: ConfigurableApplicationContext) : Unit

}
