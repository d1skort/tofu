package tofu.optics

import cats._
import cats.data.{Const, NonEmptyList}
import tofu.optics.data.Constant
import tofu.optics.data.Constant

/** aka NonEmptyFold
  * S has some occurences of A
  * and can collect then
  */
trait PReduced[-S, +T, +A, -B] extends PFolded[S, T, A, B] {
  def reduceMap[X: Semigroup](s: S)(f: A => X): X

  def foldMap[X: Monoid](s: S)(f: A => X): X = reduceMap(s)(f)
  def getAll1(s: S): NonEmptyList[A] = reduceMap(s)(NonEmptyList.one[A])
}

object Reduced extends MonoOpticCompanion(PReduced)

object PReduced extends OpticCompanion[PReduced] {
  def compose[S, T, A, B, U, V](f: PReduced[A, B, U, V], g: PReduced[S, T, A, B]): PReduced[S, T, U, V] =
    new PReduced[S, T, U, V] {
      def reduceMap[X: Semigroup](s: S)(fux: U => X): X = g.reduceMap(s)(f.reduceMap(_)(fux))
    }

  final implicit def byReducible[F[_], T, A, B](implicit F: Reducible[F]): PReduced[F[A], T, A, B] =
    new PReduced[F[A], T, A, B] {
      def reduceMap[X: Semigroup](fa: F[A])(f: A => X): X = F.reduceMap(fa)(f)
    }

  trait Context extends PRepeated.Context with PExtract.Context {
    def algebra: Semigroup[X]
    override def functor: Apply[Constant[X, *]] = {
      implicit val alg: Semigroup[X] = algebra
      Apply[Constant[X, *]]
    }
  }

  override def toGeneric[S, T, A, B](o: PReduced[S, T, A, B]): Optic[Context, S, T, A, B] =
    new Optic[Context, S, T, A, B] {
      def apply(c: Context)(p: A => Constant[c.X, B]): S => Constant[c.X, T] =
        s => Constant.Impl(o.reduceMap(s)(a => p(a).value)(c.algebra))
    }
  override def fromGeneric[S, T, A, B](o: Optic[Context, S, T, A, B]): PReduced[S, T, A, B] =
    new PReduced[S, T, A, B] {
      def reduceMap[Y: Semigroup](s: S)(f: A => Y): Y =
        o.apply(new Context {
          type X = Y
          def algebra = Semigroup[Y]
        })(a => Constant.Impl(f(a)))(s)
          .value

    }
}