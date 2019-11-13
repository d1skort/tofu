package tofu
package errorInstances

import cats.ApplicativeError
import tofu.optics.{Downcast, Upcast}

import scala.reflect.ClassTag
import cats.data.{EitherT, OptionT}
import cats.{Monad, Id}

private[tofu] class FromAppErr[F[_], E, E1](
    implicit
    protected val appErr: ApplicativeError[F, E],
    protected val sub: E1 <:< E
)

private[tofu] trait RaiseAppApErr[F[_], E, E1] extends Raise[F, E1] {
  self: FromAppErr[F, E, E1] =>
  def raise[A](err: E1): F[A] = appErr.raiseError(err)
}

private[tofu] class HandleApErr[F[_]: ApplicativeError[*[_], E], E, E1: ClassTag: * <:< E]
    extends FromAppErr[F, E, E1] with Handle.ByRecover[F, E1] {
  def recWith[A](fa: F[A])(pf: PartialFunction[E1, F[A]]): F[A] =
    appErr.recoverWith(fa)({ case e1: E1 if pf.isDefinedAt(e1) => pf(e1) })

  def restore[A](fa: F[A]): F[Option[A]] = appErr.handleError[Option[A]](appErr.map(fa)(Some(_)))(_ => None)
}

private[tofu] class FromPrism[F[_], E, E1, +TC[_[_], _], +P[_, _]](
    implicit
    protected val instance: TC[F, E],
    protected val prism: P[E, E1]
)

private[tofu] trait RaisePrism[F[_], E, E1] extends Raise[F, E1] {
  self: FromPrism[F, E, E1, Raise, Upcast] =>

  def raise[A](err: E1): F[A] = instance.raise(prism.upcast(err))
}

private[tofu] trait HandlePrism[F[_], E, E1] extends Handle[F, E1] {
  self: FromPrism[F, E, E1, Handle, Downcast] =>

  def tryHandleWith[A](fa: F[A])(f: E1 => Option[F[A]]): F[A] =
    instance.tryHandleWith(fa)(e => prism.downcast(e).flatMap(f))

  def restore[A](fa: F[A]): F[Option[A]] = instance.restore(fa)
}

private[tofu] class EitherTErrorsTo[F[_]: Monad, E] extends ErrorsTo[EitherT[F, E, *], F, E] {
  def handleWith[A](fa: EitherT[F, E, A])(f: E => F[A]): F[A] = fa.valueOrF(f)

  // Members declared in tofu.Raise
  def raise[A](err: E): EitherT[F, E, A] = EitherT.leftT[F, A](err)

  // Members declared in tofu.RestoreTo
  def restore[A](fa: EitherT[F, E, A]): F[Option[A]] = fa.toOption.value
}

private[tofu] class OptionTErrorsTo[F[_]: Monad] extends ErrorsTo[OptionT[F, *], F, Unit] {
  def handleWith[A](fa: OptionT[F, A])(f: Unit => F[A]): F[A] = fa.getOrElseF(f(()))

  // Members declared in tofu.Raise
  def raise[A](err: Unit): OptionT[F, A] = OptionT.none

  // Members declared in tofu.RestoreTo
  def restore[A](fa: OptionT[F, A]): F[Option[A]] = fa.value
}

private[tofu] class EitherErrorsTo[E] extends ErrorsTo[Either[E, *], Id, E] {
  def handleWith[A](fa: Either[E, A])(f: E => A): A = fa.fold(f, identity)

  // Members declared in tofu.Raise
  def raise[A](err: E): Either[E, A] = Left(err)

  // Members declared in tofu.RestoreTo
  def restore[A](fa: Either[E, A]): Option[A] = fa.toOption
}

private[tofu] object OptionErrorsTo extends ErrorsTo[Option, Id, Unit] {
  def handleWith[A](fa: Option[A])(f: Unit => A): A = fa.getOrElse(f(()))

  // Members declared in tofu.Raise
  def raise[A](err: Unit): Option[A] = None

  // Members declared in tofu.RestoreTo
  def restore[A](fa: Option[A]): Option[A] = fa
}
