.. _hardware-ml-tsetlin:

Tsetlin Machine
###############

The Tsetlin IP core runs Tsetlin machine inference on Muninn clause arrays. A clause
array is a content-addressable memory: each row holds the include bits of one
clause, and a search compares every row against the input literals in a single
cycle. The core adds the vote counting and the argmax on top.

On an ASIC the clause array is a prebuilt Muninn hard macro
(``muninn_tcam_<variant>_<rows>x<cols>``); its behavioural model comes from the
muninn-macros repository (``MUNINN_MACROS`` or ``$NAFARR_BASE/ext/muninn-macros``).
The variants share the pins and the cycle behaviour and differ in the layout:
``dense`` and ``speed`` are full-custom arrays optimized for area and timing,
``conservative`` is built mostly from standard cells.
On an FPGA, ``TcamModel`` implements the same behaviour with flip-flops.

All logic is bus-driven. There are no external IO signals besides the interrupt.

Features
********

* One search per start: all clauses evaluated in one cycle, result after
  ``4 + classes`` cycles
* Clause outputs, per-class sums and the winning class readable after a search
* Several macros side by side (``tiles``) for wider clauses
* Optional valid column: literal 0 is held at 0 during a search, so a row with
  include bit 0 set never matches (empty or unused clauses)
* Interrupt on search completion
* Supported buses: APB3, TileLink, Wishbone

Clause Mapping
**************

Rows are grouped by class, class-major, ``clausesPerClass`` rows each; rows behind
the last class are unused (their clause outputs are still readable). Inside a class,
even rows vote for the class and odd rows against it. A row matches when every included literal is 1.
The sum of a class is the number of matching even rows minus the number of matching
odd rows; the winner is the class with the highest sum, the lowest index on ties.

For a model with ``F`` boolean features, the literal columns are:

.. list-table::
   :widths: 25 75
   :header-rows: 1

   * - Column
     - Literal
   * - ``0``
     - Valid column (with ``validColumn``)
   * - ``1 .. F``
     - Features ``x``
   * - ``F + 1 .. 2F``
     - Negated features ``¬x``
   * - ``2F + 1 ..``
     - Unused: include bit 0, searched with 1

Trained models and a bit-exact Python reference live in the muninn-reference-models
repository.

Parameters
**********

.. list-table::
   :widths: 20 15 65
   :header-rows: 1

   * - Parameter
     - Default
     - Description
   * - ``tcam``
     - ``64 x 80``, hard, dense
     - Clause array: rows, literal columns per macro, hard macro or ``TcamModel``,
       macro variant (``Dense``, ``Speed`` or ``Conservative``)
   * - ``classes``
     - ``4``
     - Number of classes
   * - ``perClass``
     - ``0``
     - Clauses (rows) per class, even. ``0`` takes as many as fit, rounded down to
       an even number: 3 classes on 64 rows get 20 each, rows 60-63 stay unused.
   * - ``tiles``
     - ``1``
     - Macros side by side; literals = ``cols x tiles``
   * - ``validColumn``
     - ``true``
     - Hold literal 0 at 0 during searches

Register Map
************

The windows have a fixed size, so the register layout does not depend on the
configuration.

.. list-table::
   :widths: 10 20 70
   :header-rows: 1

   * - Offset
     - Name
     - Description
   * - 0x000
     - ``ip_header``
     - IP Identification header
   * - 0x004
     - ``ip_version``
     - IP Identification version
   * - 0x008
     - ``array``
     - Clause array size: ``[31:16]`` literals, ``[15:0]`` rows
   * - 0x00C
     - ``classes``
     - ``[31:16]`` clauses per class, ``[15:0]`` classes
   * - 0x010
     - ``options``
     - ``[8]`` valid column, ``[7:0]`` tiles
   * - 0x014
     - ``ctrl``
     - ``[1]`` interrupt enable, ``[0]`` start (write 1)
   * - 0x018
     - ``status``
     - ``[1]`` done (write 1 to clear), ``[0]`` busy
   * - 0x01C
     - ``row``
     - Row address for the next commit
   * - 0x020
     - ``commit``
     - Write: copy the row buffer into the clause array row
   * - 0x024
     - ``result``
     - ``[31:16]`` winning sum (signed), ``[15:0]`` winning class
   * - 0x100 + 4w
     - ``data[w]``
     - Row buffer, include bits of literals ``32w .. 32w + 31``
   * - 0x200 + 4w
     - ``literals[w]``
     - Search literals ``32w .. 32w + 31``
   * - 0x300 + 4w
     - ``matches[w]``
     - Clause outputs of the last search, rows ``32w .. 32w + 31``
   * - 0x400 + 4c
     - ``sums[c]``
     - Sum of class ``c`` (signed)

Programming
***********

Writing a clause: fill ``data``, write the row address to ``row``, then write
``commit``. Rows are written whole; the include bits cannot be read back.

Running a search: fill ``literals``, write ``start`` (keeping the interrupt enable
bit), wait for ``done`` or the interrupt, read ``result``, and clear ``done``.

The C driver (``software/include/tsetlin.h``) wraps both:
``tsetlin_write_clause()``, ``tsetlin_search_start()``, ``tsetlin_search_done()``,
``tsetlin_result()`` and ``tsetlin_class_sum()``.
