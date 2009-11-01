package org.jboss.weld.test.unit.deployment.structure.extensions;

import javax.enterprise.inject.spi.Extension;

import org.jboss.weld.BeanManagerImpl;
import org.jboss.weld.bootstrap.spi.BeanDeploymentArchive;
import org.jboss.weld.bootstrap.spi.Deployment;
import org.jboss.weld.mock.AbstractMockDeployment;
import org.jboss.weld.mock.MockBeanDeploymentArchive;
import org.jboss.weld.mock.MockServletLifecycle;
import org.jboss.weld.mock.TestContainer;
import org.jboss.weld.util.serviceProvider.PackageServiceLoaderFactory;
import org.jboss.weld.util.serviceProvider.ServiceLoaderFactory;
import org.testng.annotations.Test;

public class NonBDAExtensionTest
{
   
   @Test(description="WELD-233")
   public void test()
   {
      // Create the BDA in which we will deploy Observer1 and Foo. This is equivalent to a war or ejb jar
      final MockBeanDeploymentArchive bda1 = new MockBeanDeploymentArchive("1", Observer1.class, Foo.class);
      
      // Create the BDA to return from loadBeanDeploymentArchive for Observer2, this is probably a library, though could be another war or ejb jar
      // bda2 is accessible from bda1, but isn't added to it's accessibility graph by default. This similar to an archive which doesn't contain a beans.xml but does contain an extension 
      final BeanDeploymentArchive bda2 = new MockBeanDeploymentArchive("2", Observer2.class);
      
      // Create a deployment, that we can use to mirror the structure of one Extension inside a BDA, and one outside
      Deployment deployment = new AbstractMockDeployment(bda1)
      {
         
         public BeanDeploymentArchive loadBeanDeploymentArchive(Class<?> beanClass)
         {
            // Return bda2 if it is Observer2. Stick anything else which this test isn't about in bda1
            if (beanClass.equals(Observer2.class))
            {
               // If Observer2 is requested, then we need to add bda2 to the accessibility graph of bda1
               bda1.getBeanDeploymentArchives().add(bda2);
               return bda2;
            }
            else
            {
               return bda1;
            }
         }

      };
      
      TestContainer container = new TestContainer(new MockServletLifecycle(deployment, bda1));
      container.getLifecycle().initialize();
      
      // Add custom ServiceLoader so that we can load Extension services from current package, not META-INF/services
      // We do this after startContainer() so we replace the default impl
      deployment.getServices().add(ServiceLoaderFactory.class, new PackageServiceLoaderFactory(NonBDAExtensionTest.class.getPackage(), Extension.class));
      
      // Cause the container to deploy the beans etc.
      container.getLifecycle().beginApplication();
      
      // Get the bean manager for bda1 and bda2
      BeanManagerImpl beanManager1 = container.getBeanManager();
      BeanManagerImpl beanManager2 = container.getLifecycle().getBootstrap().getManager(bda2);
      
      Observer1 observer1 = beanManager1.getInstanceByType(Observer1.class);
      assert observer1.isBeforeBeanDiscoveryCalled();
      assert observer1.isAfterBeanDiscoveryCalled();
      assert observer1.isAfterDeploymentValidationCalled();
      assert observer1.isProcessInjectionTargetCalled();
      assert observer1.isProcessManagedBeanCalled();
      assert observer1.isProcessProducerCalled();
      
      assert beanManager2.getBeans(Observer2.class).size() == 1;
      // Also check that the accessibility graph has been updated
      assert beanManager1.getBeans(Observer2.class).size() == 1;
      
      Observer2 observer2 = beanManager2.getInstanceByType(Observer2.class);
      assert observer2.isBeforeBeanDiscoveryCalled();
      assert observer2.isAfterBeanDiscoveryCalled();
      assert observer2.isAfterDeploymentValidationCalled();
      assert observer2.isProcessInjectionTargetCalled();
      assert observer2.isProcessManagedBeanCalled();
      assert observer2.isProcessProducerCalled();
      
   }
   
}
