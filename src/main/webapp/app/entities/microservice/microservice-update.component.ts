import { Component, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';

import { IMicroservice, Microservice } from 'app/shared/model/microservice.model';
import { MicroserviceService } from './microservice.service';
import { ITeam } from 'app/shared/model/team.model';
import { TeamService } from 'app/entities/team/team.service';

@Component({
  selector: 'jhi-microservice-update',
  templateUrl: './microservice-update.component.html',
})
export class MicroserviceUpdateComponent implements OnInit {
  isSaving = false;
  teams: ITeam[] = [];

  editForm = this.fb.group({
    id: [],
    name: [],
    description: [],
    imageUrl: [],
    swaggerUrl: [],
    gitUrl: [],
    team: [],
  });

  constructor(
    protected microserviceService: MicroserviceService,
    protected teamService: TeamService,
    protected activatedRoute: ActivatedRoute,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.activatedRoute.data.subscribe(({ microservice }) => {
      this.updateForm(microservice);

      this.teamService.query().subscribe((res: HttpResponse<ITeam[]>) => (this.teams = res.body || []));
    });
  }

  updateForm(microservice: IMicroservice): void {
    this.editForm.patchValue({
      id: microservice.id,
      name: microservice.name,
      description: microservice.description,
      imageUrl: microservice.imageUrl,
      swaggerUrl: microservice.swaggerUrl,
      gitUrl: microservice.gitUrl,
      team: microservice.team,
    });
  }

  previousState(): void {
    window.history.back();
  }

  save(): void {
    this.isSaving = true;
    const microservice = this.createFromForm();
    if (microservice.id !== undefined) {
      this.subscribeToSaveResponse(this.microserviceService.update(microservice));
    } else {
      this.subscribeToSaveResponse(this.microserviceService.create(microservice));
    }
  }

  private createFromForm(): IMicroservice {
    return {
      ...new Microservice(),
      id: this.editForm.get(['id'])!.value,
      name: this.editForm.get(['name'])!.value,
      description: this.editForm.get(['description'])!.value,
      imageUrl: this.editForm.get(['imageUrl'])!.value,
      swaggerUrl: this.editForm.get(['swaggerUrl'])!.value,
      gitUrl: this.editForm.get(['gitUrl'])!.value,
      team: this.editForm.get(['team'])!.value,
    };
  }

  protected subscribeToSaveResponse(result: Observable<HttpResponse<IMicroservice>>): void {
    result.subscribe(
      () => this.onSaveSuccess(),
      () => this.onSaveError()
    );
  }

  protected onSaveSuccess(): void {
    this.isSaving = false;
    this.previousState();
  }

  protected onSaveError(): void {
    this.isSaving = false;
  }

  trackById(index: number, item: ITeam): any {
    return item.id;
  }
}