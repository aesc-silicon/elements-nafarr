Getting Started
###############

Install sbt
***********

We strongly recommend using the official installer available on the `Scala homepage`_. Alternatively, sbt offers a comprehensive installation guide with all necessary commands listed on the `sbt homepage`_.

To ensure sbt is installed correctly, you can compile all Scala modules by running:

.. code-block:: bash

    sbt compile

.. _Scala homepage: https://www.scala-lang.org/download/
.. _sbt homepage: https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Linux.html

Run Tests
*********

SpinalHDL's test suite requires a version of Verilator that is newer than what is available in most package repositories. As a workaround, download and extract the `OSS CAD Suite`_ archive for your platform.

.. code-block:: bash

   wget https://github.com/YosysHQ/oss-cad-suite-build/releases/download/2024-01-01/oss-cad-suite-linux-x64-20240101.tgz
   tar -xvf oss-cad-suite-linux-x64-20240101.tgz
   rm oss-cad-suite-linux-x64-20240101.tgz

Remember to update the path to the OSS CAD Suite and export it in every new bash session:

.. code-block:: bash

   # Add OSS CAD Suite pre-builts to the environment
   export PATH=$PWD/oss-cad-suite/bin/:$PATH
   export NAFARR_BASE=$PWD

.. _OSS CAD Suite: https://github.com/YosysHQ/oss-cad-suite-build/releases/tag/2024-01-01

To run all available tests, use the following sbt command:

.. code-block:: bash

    sbt test

If you need to test a specific component, use the `testOnly` command:

.. code-block:: bash

    sbt "testOnly *Apb3GpioTest"

Documentation
*************

Begin by creating a virtual environment and installing the necessary packages for documentation:

.. code-block:: bash

    virtualenv venv
    source venv/bin/activate
    pip install -r docs/requirements.txt

Then, navigate to the `docs/` directory and generate the HTML documentation. Open the output in Firefox:

.. code-block:: bash

    cd docs/
    make html
    firefox build/html/index.html
